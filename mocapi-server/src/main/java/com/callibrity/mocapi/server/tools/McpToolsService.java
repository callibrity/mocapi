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

import static com.callibrity.mocapi.model.McpMethods.TOOLS_CALL;
import static com.callibrity.mocapi.model.McpMethods.TOOLS_LIST;

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolException;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.dispatch.DispatchChains;
import com.callibrity.mocapi.server.dispatch.McpDispatchInterceptor;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.guards.Guards;
import com.callibrity.mocapi.server.mrtr.ElicitationLedgerMismatchException;
import com.callibrity.mocapi.server.mrtr.InputRequiredException;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.ReplayOutcome;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.server.util.PaginatedService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/**
 * Manages tool registration and JSON-RPC dispatch. Input validation runs as a per-handler
 * interceptor wired into each {@link CallToolHandler}'s invoker; see {@link
 * InputSchemaValidatingInterceptor}.
 */
public class McpToolsService extends PaginatedService<CallToolHandler, Tool>
    implements ToolCallReplayInvoker {

  private static final String INVALID_STRUCTURED_CONTENT_SHAPE =
      "McpToolException structuredContent must serialize to a JSON object, but %s serialized to "
          + "a %s node. Use a record/POJO (or ObjectNode) whose Jackson serialization is an "
          + "object for error payloads.";

  private final Logger log = LoggerFactory.getLogger(McpToolsService.class);
  private final ObjectMapper objectMapper;
  private final MrtrElicitationEngine elicitationEngine;
  private final CacheSettings cacheSettings;
  private final List<McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>>
      dispatchInterceptors;

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine) {
    this(handlers, objectMapper, elicitationEngine, DEFAULT_PAGE_SIZE, CacheSettings.defaults());
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize) {
    this(handlers, objectMapper, elicitationEngine, pageSize, CacheSettings.defaults());
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize,
      CacheSettings cacheSettings) {
    this(handlers, objectMapper, elicitationEngine, pageSize, cacheSettings, List.of());
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize,
      CacheSettings cacheSettings,
      List<McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>> dispatchInterceptors) {
    super(
        handlers,
        CallToolHandler::name,
        CallToolHandler::descriptor,
        Comparator.comparing(Tool::name),
        "Tool",
        pageSize);
    this.objectMapper = objectMapper;
    this.elicitationEngine = elicitationEngine;
    this.cacheSettings = cacheSettings;
    this.dispatchInterceptors = DispatchChains.sort(dispatchInterceptors);
  }

  /**
   * Lists registered tools sorted by tool name — the deterministic ordering the spec recommends so
   * clients can cache list responses and LLM prompt caches get stable prefixes. Cache directives
   * ({@code ttlMs}/{@code cacheScope}) come from the configured {@link CacheSettings} list values.
   */
  @JsonRpcMethod(TOOLS_LIST)
  public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
    return paginate(
        h -> Guards.allows(h.guards()),
        params,
        (tools, nextCursor) ->
            new ListToolsResult(
                tools,
                nextCursor,
                cacheSettings.listTtlMs(),
                cacheSettings.scope(),
                ResultTypes.COMPLETE));
  }

  /** Returns the full {@link Tool} descriptor for a registered tool, or {@code null} if none. */
  public Tool findToolDescriptor(String name) {
    return findByName(name).map(CallToolHandler::descriptor).orElse(null);
  }

  /** Returns every registered tool descriptor, sorted by name. */
  public List<Tool> allToolDescriptors() {
    return allDescriptors();
  }

  /**
   * Returns either a {@link CallToolResult} or an {@code InputRequiredResult} — the MRTR union the
   * spec declares for {@code tools/call} responses — so the declared type is {@link Object};
   * ripcurl serializes the runtime type. The {@link MrtrElicitationEngine} wraps the invocation:
   * this method is one of the exactly three MRTR-capable RPC seams (see the engine's javadoc).
   */
  @JsonRpcMethod(TOOLS_CALL)
  public Object callTool(@JsonRpcParams CallToolRequestParams params) {
    String name = params.name();
    log.debug("Received request to call tool \"{}\"", name);
    JsonNode args =
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode();
    CallToolHandler handler = lookup(name);
    return DispatchChains.run(
        dispatchInterceptors,
        handler,
        params,
        () ->
            elicitationEngine.execute(
                TOOLS_CALL,
                params,
                params.inputResponses(),
                params.requestState(),
                () -> invokeTool(name, handler, args, params)));
  }

  private CallToolResult invokeTool(
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
   * Unlike {@link #callTool}, there is no wire {@code requestState} to encode/decode — the caller
   * owns the ledger's identity and lifecycle; {@link ElicitationLedgerMismatchException} propagates
   * to the caller rather than being translated to a JSON-RPC error here, since there is no JSON-RPC
   * response to shape.
   *
   * <p>{@link McpExchange#CURRENT} is bound to the supplied {@code exchange} around the whole
   * replay so the handler chain sees the same exchange it would on the wire path; {@link
   * McpTransport#CURRENT} is deliberately left unbound here, since a detached invocation has no
   * transport to bind.
   */
  @Override
  public Outcome invoke(
      String toolName,
      JsonNode arguments,
      List<ResponseLedgerEntry> ledger,
      McpProgressSource progressOverride,
      McpExchange exchange) {
    CallToolHandler handler = lookup(toolName);
    DefaultMcpToolContext ctx =
        new DefaultMcpToolContext(progressOverride, elicitationEngine, exchange, toolName);
    ReplayOutcome outcome =
        exchange != null
            ? ScopedValue.where(McpExchange.CURRENT, exchange)
                .call(
                    () ->
                        elicitationEngine
                            .replayExecutor()
                            .execute(
                                ledger, () -> invokeWithContext(toolName, handler, arguments, ctx)))
            : elicitationEngine
                .replayExecutor()
                .execute(ledger, () -> invokeWithContext(toolName, handler, arguments, ctx));
    if (outcome instanceof ReplayOutcome.InputRequired ir) {
      return new Outcome.InputRequired(ir.key(), new ElicitRequest(ir.params()), ir.entries());
    }
    return new Outcome.Completed((CallToolResult) ((ReplayOutcome.Completed) outcome).result());
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
