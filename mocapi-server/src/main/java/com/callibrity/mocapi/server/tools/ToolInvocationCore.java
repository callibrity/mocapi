/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolException;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.mrtr.ElicitationLedgerMismatchException;
import com.callibrity.mocapi.server.mrtr.InputRequiredException;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.ReplayExecutor;
import com.callibrity.mocapi.server.mrtr.ReplayOutcome;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/**
 * Owns tool-handler lookup and invocation mechanics shared by both {@code tools/call} paths (ADR
 * -0039): the on-dispatch-thread wire path ({@link McpToolsService#callTool}) and the detached,
 * off-dispatch-thread replay path ({@link ToolCallReplayInvoker#invoke}, which this class
 * implements). Both paths build a {@link DefaultMcpToolContext}, run the handler under the same
 * {@code ScopedValue} bindings, and map the outcome (success, tool exception, guard denial, or
 * unhandled exception) to a {@link CallToolResult} through the identical exception cascade.
 *
 * <p>Extracted from {@code McpToolsService} so a detached carrier (e.g. {@code
 * TaskExecutionEngine}) can depend on this invocation core directly instead of on the full tools
 * service — which also collects that carrier's dispatch interceptor, and would otherwise close a
 * bean-graph cycle.
 *
 * <p>Delegates handler lookup to the shared {@link CallToolHandlerRegistry}, built from the same
 * {@link CallToolHandler} list {@link McpToolsService} registers; {@code McpToolsService}'s
 * registry/pagination (list, cursor, guard-filtered visibility) is unrelated to invocation and
 * stays there.
 */
public final class ToolInvocationCore implements ToolCallReplayInvoker {

  private static final String INVALID_STRUCTURED_CONTENT_SHAPE =
      "McpToolException structuredContent must serialize to a JSON object, but %s serialized to "
          + "a %s node. Use a record/POJO (or ObjectNode) whose Jackson serialization is an "
          + "object for error payloads.";

  private final Logger log = LoggerFactory.getLogger(ToolInvocationCore.class);
  private final CallToolHandlerRegistry registry;
  private final ObjectMapper objectMapper;
  private final MrtrElicitationEngine elicitationEngine;
  private final ReplayExecutor replayExecutor;

  public ToolInvocationCore(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      ReplayExecutor replayExecutor) {
    this(new CallToolHandlerRegistry(handlers), objectMapper, elicitationEngine, replayExecutor);
  }

  /**
   * Primary constructor (ADR-0039): takes the shared {@link CallToolHandlerRegistry} bean directly
   * rather than a bare handler list, so autoconfigure never has to expose a fragile {@code
   * List<CallToolHandler>} Spring injection point (see the registry's javadoc).
   */
  public ToolInvocationCore(
      CallToolHandlerRegistry registry,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      ReplayExecutor replayExecutor) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.elicitationEngine = Objects.requireNonNull(elicitationEngine, "elicitationEngine");
    this.replayExecutor = Objects.requireNonNull(replayExecutor, "replayExecutor");
  }

  /**
   * On-dispatch-thread invocation for {@code tools/call}: builds the context from the current
   * {@link McpTransport}/{@link McpExchange} and the request's progress token.
   */
  CallToolResult invokeTool(
      String name, CallToolHandler handler, JsonNode args, CallToolRequestParams params) {
    McpTransport transport = McpTransport.CURRENT.isBound() ? McpTransport.CURRENT.get() : null;
    McpExchange exchange = McpExchange.CURRENT.isBound() ? McpExchange.CURRENT.get() : null;
    ValueNode progressToken = params.meta() != null ? params.meta().progressToken() : null;
    DefaultMcpToolContext ctx =
        new DefaultMcpToolContext(transport, progressToken, elicitationEngine, exchange, name);
    return invokeWithContext(name, handler, args, ctx);
  }

  /**
   * Detached (off-dispatch-thread) re-invocation of a tool by name (ADR-0021/ADR-0025 seam), driven
   * by a caller-supplied response ledger rather than the {@code tools/call} request/retry envelope.
   * Unlike the wire path, there is no {@code requestState} to encode/decode — the caller owns the
   * ledger's identity and lifecycle; {@link ElicitationLedgerMismatchException} propagates to the
   * caller rather than being translated to a JSON-RPC error here, since there is no JSON-RPC
   * response to shape.
   *
   * <p>{@link McpExchange#CURRENT} is bound to the supplied {@code exchange} around the whole
   * replay so the handler chain sees the same exchange it would on the wire path; {@link
   * McpTransport#CURRENT} is deliberately left unbound here, since a detached invocation has no
   * transport to bind.
   */
  @Override
  public ReplayOutcome<CallToolResult, ElicitRequest> invoke(
      String toolName,
      JsonNode arguments,
      List<ResponseLedgerEntry> ledger,
      McpProgressSource progress,
      McpExchange exchange) {
    CallToolHandler handler = registry.lookup(toolName);
    DefaultMcpToolContext ctx =
        new DefaultMcpToolContext(progress, elicitationEngine, exchange, toolName);
    ReplayOutcome<Object, ElicitRequestFormParams> outcome =
        exchange != null
            ? ScopedValue.where(McpExchange.CURRENT, exchange)
                .call(
                    () ->
                        replayExecutor.execute(
                            ledger, () -> invokeWithContext(toolName, handler, arguments, ctx)))
            : replayExecutor.execute(
                ledger, () -> invokeWithContext(toolName, handler, arguments, ctx));
    return switch (outcome) {
      case ReplayOutcome.InputRequired<?, ?> ir ->
          new ReplayOutcome.InputRequired<>(
              ir.key(), new ElicitRequest((ElicitRequestFormParams) ir.request()), ir.ledger());
      case ReplayOutcome.Completed<?, ?> completed ->
          new ReplayOutcome.Completed<>((CallToolResult) completed.result());
    };
  }

  private CallToolResult invokeWithContext(
      String name, CallToolHandler handler, JsonNode args, DefaultMcpToolContext ctx) {
    try {
      Object result =
          ScopedValue.where(McpToolContext.CURRENT, ctx)
              .where(McpElicitor.CURRENT, ctx)
              .call(() -> handler.call(args));
      return handler.resultMapper().map(result);
    } catch (InputRequiredException
        | ElicitationLedgerMismatchException
        | McpElicitationNotSupportedException e) {
      // MRTR control flow and capability gating must reach the engine / dispatch error handling,
      // not be wrapped into an isError CallToolResult: the pending signal becomes the
      // InputRequiredResult, the ledger mismatch becomes -32602, and the missing capability
      // becomes the spec's MissingRequiredClientCapabilityError (-32021).
      throw e;
    } catch (McpToolException e) {
      // Tool authors' preferred way to signal a tool-execution failure with structured detail.
      // Caught BEFORE the generic JsonRpcException / Exception arms so subclasses surface via
      // toErrorCallToolResult(McpToolException, ...), preserving their structuredContent and
      // additional content blocks.
      log.warn("Tool {} raised a tool execution error", name, e);
      try {
        return toErrorCallToolResult(e, objectMapper);
      } catch (IllegalStateException bad) {
        // The author's structuredContent payload didn't serialize to a JSON object. Rather than
        // letting IllegalStateException bubble out of callTool (which would be a 500 to the
        // client), degrade gracefully to a text-only error result whose message is the detailed
        // diagnostic — surfacing the bug to the author in the tool response itself.
        log.warn("Tool {} raised McpToolException with invalid structuredContent shape", name, bad);
        return toErrorCallToolResult(bad);
      }
    } catch (JsonRpcException e) {
      // Guard denials (-32010, ADR-0023) are a protocol-level gate and must surface as JSON-RPC
      // errors, not
      // be wrapped into a CallToolResult. Schema-validation JsonRpcExceptions (-32602) stay wrapped
      // so the calling LLM can self-correct on malformed arguments.
      if (e.getCode() == JsonRpcErrorCodes.FORBIDDEN) {
        throw e;
      }
      log.warn("Tool {} threw an unhandled exception", name, e);
      return toErrorCallToolResult(e);
    } catch (Exception e) {
      log.warn("Tool {} threw an unhandled exception", name, e);
      return toErrorCallToolResult(e);
    }
  }

  static CallToolResult toErrorCallToolResult(Throwable throwable) {
    String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    return new CallToolResult(
        List.of(new TextContent(message, null)), true, null, ResultTypes.COMPLETE);
  }

  static CallToolResult toErrorCallToolResult(McpToolException e, ObjectMapper objectMapper) {
    String message = e.getMessage() != null ? e.getMessage() : e.toString();
    List<ContentBlock> content = new ArrayList<>();
    content.add(new TextContent(message, null));
    List<ContentBlock> additional = e.getAdditionalContent();
    if (additional != null && !additional.isEmpty()) {
      content.addAll(additional);
    }
    ObjectNode structured = null;
    Object payload = e.getStructuredContent();
    if (payload != null) {
      JsonNode node = objectMapper.valueToTree(payload);
      if (!(node instanceof ObjectNode obj)) {
        throw new IllegalStateException(
            String.format(
                INVALID_STRUCTURED_CONTENT_SHAPE,
                payload.getClass().getName(),
                node.getNodeType()));
      }
      structured = obj;
    }
    return new CallToolResult(List.copyOf(content), true, structured, ResultTypes.COMPLETE);
  }
}
