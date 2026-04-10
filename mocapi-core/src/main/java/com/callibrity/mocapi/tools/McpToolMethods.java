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

import com.callibrity.mocapi.http.McpRequestId;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStream;
import com.callibrity.mocapi.stream.DefaultMcpStreamContext;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ValueNode;

@Slf4j
@JsonRpcService
@RequiredArgsConstructor
public class McpToolMethods {

  public static final String SSE_EMITTER_KEY = "sseEmitter";

  private final ToolsRegistry toolsRegistry;
  private final ObjectMapper objectMapper;
  private final MailboxFactory mailboxFactory;
  private final SchemaGenerator schemaGenerator;
  private final McpSessionService sessionService;
  private final Duration elicitationTimeout;

  @JsonRpcMethod("tools/list")
  public ListToolsResult listTools(String cursor) {
    return toolsRegistry.listTools(cursor);
  }

  @JsonRpcMethod("tools/call")
  public Object callTool(@JsonRpcParams CallToolRequestParams params) {
    String name = params.name();
    JsonNode args =
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode();
    McpTool tool = toolsRegistry.lookup(name);

    if (!tool.isStreamable()) {
      return toolsRegistry.callTool(name, args);
    }

    ValueNode progressToken = params.meta() != null ? params.meta().progressToken() : null;
    JsonNode requestId = McpRequestId.CURRENT.isBound() ? McpRequestId.CURRENT.get() : null;
    McpSession session = McpSession.CURRENT.get();
    String sessionId = session.sessionId();

    McpSessionStream stream = sessionService.createStream(sessionId);
    DefaultMcpStreamContext<?> ctx =
        new DefaultMcpStreamContext<>(
            stream,
            objectMapper,
            progressToken,
            mailboxFactory,
            schemaGenerator,
            sessionService,
            sessionId,
            elicitationTimeout,
            requestId);

    SseEmitter emitter = stream.subscribe();

    Thread.ofVirtual()
        .start(
            () ->
                ScopedValue.where(McpSession.CURRENT, session)
                    .where(McpStreamContext.CURRENT, ctx)
                    .run(() -> executeStreamableTool(name, args, requestId, stream, ctx)));

    return new JsonRpcResult(NullNode.getInstance(), requestId)
        .withMetadata(SSE_EMITTER_KEY, emitter);
  }

  private void executeStreamableTool(
      String name,
      JsonNode arguments,
      JsonNode requestId,
      McpSessionStream stream,
      DefaultMcpStreamContext<?> ctx) {
    try {
      CallToolResult result = toolsRegistry.callTool(name, arguments);
      if (!ctx.isResultSent()) {
        JsonRpcResult response = new JsonRpcResult(objectMapper.valueToTree(result), requestId);
        stream.publishJson(objectMapper.valueToTree(response));
      }
    } catch (JsonRpcException e) {
      if (!ctx.isResultSent()) {
        stream.publishJson(
            objectMapper.valueToTree(new JsonRpcError(e.getCode(), e.getMessage(), requestId)));
      }
    } catch (Exception e) {
      log.warn("Tool {} threw an unhandled exception", name, e);
      if (!ctx.isResultSent()) {
        CallToolResult errorResult = ToolsRegistry.toErrorCallToolResult(e);
        JsonRpcResult response =
            new JsonRpcResult(objectMapper.valueToTree(errorResult), requestId);
        stream.publishJson(objectMapper.valueToTree(response));
      }
    } finally {
      if (!ctx.isResultSent()) {
        stream.close();
      }
    }
  }
}
