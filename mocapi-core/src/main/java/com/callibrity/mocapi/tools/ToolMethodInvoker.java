/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.tools;

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.stream.DefaultMcpStreamContext;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Orchestrates tool invocation, handling both synchronous (non-streamable) and asynchronous
 * (streamable) tool calls. For streamable tools, sets up an Odyssey stream, dispatches the tool on
 * a virtual thread with {@link ScopedValue} context, and makes the {@link SseEmitter} available via
 * {@link #SSE_EMITTER_HOLDER}.
 */
@Slf4j
@RequiredArgsConstructor
public class ToolMethodInvoker {

  /** Metadata key for the SSE emitter (MCP transport concern). */
  public static final String SSE_EMITTER_KEY = "sseEmitter";

  /**
   * Carrier for passing the {@link SseEmitter} from the handler back to the controller. The
   * controller binds an {@link AtomicReference} before dispatch; the handler sets the emitter on it
   * for streamable tools.
   */
  public static final ScopedValue<AtomicReference<SseEmitter>> SSE_EMITTER_HOLDER =
      ScopedValue.newInstance();

  /**
   * The JSON-RPC request ID, set by the controller before dispatch. Needed by the virtual thread to
   * construct the final JSON-RPC response on the stream.
   */
  public static final ScopedValue<JsonNode> REQUEST_ID = ScopedValue.newInstance();

  private final ToolsRegistry toolsRegistry;
  private final OdysseyStreamRegistry odysseyRegistry;
  private final ObjectMapper objectMapper;
  private final MailboxFactory mailboxFactory;
  private final SchemaGenerator schemaGenerator;
  private final McpSessionService sessionService;
  private final Duration elicitationTimeout;

  /**
   * Invokes a tool by name. For non-streamable tools, calls synchronously and returns the result.
   * For streamable tools, sets up an SSE stream, dispatches on a virtual thread, and returns a
   * placeholder response (the real result is published on the stream).
   *
   * @param name the tool name
   * @param arguments the tool arguments (never null)
   * @param progressToken the progress token from request metadata (may be null)
   * @return the call tool response (placeholder for streamable tools)
   */
  public ToolsRegistry.CallToolResponse invoke(
      String name, ObjectNode arguments, String progressToken) {
    McpTool tool = toolsRegistry.lookup(name);

    if (!tool.isStreamable()) {
      return toolsRegistry.callTool(name, arguments);
    }

    JsonNode requestId = REQUEST_ID.isBound() ? REQUEST_ID.get() : null;
    McpSession session = McpSession.CURRENT.get();
    String sessionId = McpSession.CURRENT_ID.get();

    OdysseyStream stream = odysseyRegistry.ephemeral();
    DefaultMcpStreamContext ctx =
        new DefaultMcpStreamContext(
            stream,
            objectMapper,
            progressToken,
            mailboxFactory,
            schemaGenerator,
            sessionService,
            sessionId,
            elicitationTimeout);

    stream.publishJson(Map.of());
    SseEmitter emitter = stream.subscribe();
    SSE_EMITTER_HOLDER.get().set(emitter);

    Thread.ofVirtual()
        .start(
            () ->
                ScopedValue.where(McpSession.CURRENT, session)
                    .where(McpSession.CURRENT_ID, sessionId)
                    .where(McpStreamContext.CURRENT, ctx)
                    .run(() -> executeStreamableTool(name, arguments, requestId, stream)));

    return new ToolsRegistry.CallToolResponse(List.of(), null, objectMapper.createObjectNode());
  }

  private void executeStreamableTool(
      String name, ObjectNode arguments, JsonNode requestId, OdysseyStream stream) {
    try {
      ToolsRegistry.CallToolResponse result = toolsRegistry.callTool(name, arguments);
      ObjectNode response = buildJsonRpcResponse(requestId, objectMapper.valueToTree(result));
      stream.publishJson(response);
    } catch (JsonRpcException e) {
      stream.publishJson(buildJsonRpcError(requestId, e.getCode(), e.getMessage()));
    } catch (Exception e) {
      log.error("Unexpected error executing streamable tool {}", name, e);
      stream.publishJson(
          buildJsonRpcError(requestId, JsonRpcException.INTERNAL_ERROR, e.getMessage()));
    } finally {
      stream.close();
    }
  }

  private ObjectNode buildJsonRpcResponse(JsonNode id, JsonNode result) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", "2.0");
    node.set("result", result);
    if (id != null) {
      node.set("id", id);
    }
    return node;
  }

  private ObjectNode buildJsonRpcError(JsonNode id, int code, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", "2.0");
    if (id != null) {
      node.set("id", id);
    }
    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    node.set("error", error);
    return node;
  }
}
