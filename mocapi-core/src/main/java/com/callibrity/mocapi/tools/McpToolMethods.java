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

import com.callibrity.mocapi.content.CallToolResponse;
import com.callibrity.mocapi.http.McpRequestId;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStream;
import com.callibrity.mocapi.stream.DefaultMcpStreamContext;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.Named;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

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
  public ToolsRegistry.ListToolsResponse listTools(String cursor) {
    return toolsRegistry.listTools(cursor);
  }

  @JsonRpcMethod("tools/call")
  public Object callTool(String name, JsonNode arguments, @Named("_meta") McpRequestMeta meta) {
    JsonNode args = arguments != null ? arguments : objectMapper.createObjectNode();
    McpTool tool = toolsRegistry.lookup(name);

    if (!tool.isStreamable()) {
      return toolsRegistry.callTool(name, args);
    }

    String progressToken = meta != null ? meta.progressToken() : null;
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
      CallToolResponse result = toolsRegistry.callTool(name, arguments);
      if (!ctx.isResponseSent()) {
        JsonRpcResult response = new JsonRpcResult(objectMapper.valueToTree(result), requestId);
        stream.publishJson(objectMapper.valueToTree(response));
      }
    } catch (JsonRpcException e) {
      if (!ctx.isResponseSent()) {
        stream.publishJson(
            objectMapper.valueToTree(new JsonRpcError(e.getCode(), e.getMessage(), requestId)));
      }
    } catch (Exception e) {
      log.error("Unexpected error executing streamable tool {}", name, e);
      if (!ctx.isResponseSent()) {
        stream.publishJson(
            objectMapper.valueToTree(
                new JsonRpcError(JsonRpcProtocol.INTERNAL_ERROR, e.getMessage(), requestId)));
      }
    } finally {
      if (!ctx.isResponseSent()) {
        stream.close();
      }
    }
  }
}
