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

import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;

import com.callibrity.mocapi.http.McpRequestId;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.stream.DefaultMcpStreamContext;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Orchestrates tool invocation, handling both synchronous (non-streamable) and asynchronous
 * (streamable) tool calls. For streamable tools, sets up an Odyssey stream and dispatches the tool
 * on a virtual thread. The {@link SseEmitter} is returned to the controller via {@link
 * JsonRpcResponse} metadata.
 */
@Slf4j
@RequiredArgsConstructor
public class ToolMethodInvoker {

  /** Metadata key for the SSE emitter (MCP transport concern). */
  public static final String SSE_EMITTER_KEY = "sseEmitter";

  private final ToolsRegistry toolsRegistry;
  private final OdysseyStreamRegistry odysseyRegistry;
  private final ObjectMapper objectMapper;
  private final MailboxFactory mailboxFactory;
  private final SchemaGenerator schemaGenerator;
  private final McpSessionService sessionService;
  private final Duration elicitationTimeout;

  /**
   * Invokes a tool by name. For non-streamable tools, calls synchronously and returns the result as
   * a {@link ToolsRegistry.CallToolResponse}. For streamable tools, sets up an SSE stream,
   * dispatches on a virtual thread, and returns a {@link JsonRpcResponse} carrying the emitter in
   * metadata.
   *
   * @param name the tool name
   * @param arguments the tool arguments (never null)
   * @param progressToken the progress token from request metadata (may be null)
   * @return a {@link ToolsRegistry.CallToolResponse} or a {@link JsonRpcResponse} with emitter
   *     metadata
   */
  public Object invoke(String name, ObjectNode arguments, String progressToken) {
    McpTool tool = toolsRegistry.lookup(name);

    if (!tool.isStreamable()) {
      return toolsRegistry.callTool(name, arguments);
    }

    JsonNode requestId = McpRequestId.CURRENT.isBound() ? McpRequestId.CURRENT.get() : null;
    McpSession session = McpSession.CURRENT.get();
    String sessionId = session.sessionId();

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

    Thread.ofVirtual()
        .start(
            () ->
                ScopedValue.where(McpSession.CURRENT, session)
                    .where(McpStreamContext.CURRENT, ctx)
                    .run(() -> executeStreamableTool(name, arguments, requestId, stream)));

    return new JsonRpcResponse(NullNode.getInstance(), requestId)
        .withMetadata(SSE_EMITTER_KEY, emitter);
  }

  private void executeStreamableTool(
      String name, ObjectNode arguments, JsonNode requestId, OdysseyStream stream) {
    try {
      ToolsRegistry.CallToolResponse result = toolsRegistry.callTool(name, arguments);
      JsonRpcResponse response =
          new JsonRpcResponse(VERSION, objectMapper.valueToTree(result), requestId);
      stream.publishJson(objectMapper.valueToTree(response));
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

  private ObjectNode buildJsonRpcError(JsonNode id, int code, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", VERSION);
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
