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
package com.callibrity.mocapi.server.tools;

import static com.callibrity.mocapi.model.McpMethods.TOOLS_CALL;
import static com.callibrity.mocapi.model.McpMethods.TOOLS_LIST;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.guards.Guards;
import com.callibrity.mocapi.server.util.PaginatedService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
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
public class McpToolsService extends PaginatedService<CallToolHandler, Tool> {

  private final Logger log = LoggerFactory.getLogger(McpToolsService.class);
  private final ObjectMapper objectMapper;
  private final McpResponseCorrelationService correlationService;

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      McpResponseCorrelationService correlationService) {
    this(handlers, objectMapper, correlationService, DEFAULT_PAGE_SIZE);
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      McpResponseCorrelationService correlationService,
      int pageSize) {
    super(
        handlers,
        CallToolHandler::name,
        CallToolHandler::descriptor,
        Comparator.comparing(Tool::name),
        "Tool",
        pageSize);
    this.objectMapper = objectMapper;
    this.correlationService = correlationService;
  }

  @JsonRpcMethod(TOOLS_LIST)
  public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
    return paginate(h -> Guards.allows(h.guards()), params, ListToolsResult::new);
  }

  /** Returns the full {@link Tool} descriptor for a registered tool, or {@code null} if none. */
  public Tool findToolDescriptor(String name) {
    return findByName(name).map(CallToolHandler::descriptor).orElse(null);
  }

  /** Returns every registered tool descriptor, sorted by name. */
  public List<Tool> allToolDescriptors() {
    return allDescriptors();
  }

  @JsonRpcMethod(TOOLS_CALL)
  public CallToolResult callTool(@JsonRpcParams CallToolRequestParams params) {
    String name = params.name();
    log.debug("Received request to call tool \"{}\"", name);
    JsonNode args =
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode();
    CallToolHandler handler = lookup(name);
    return invokeTool(name, handler, args, params);
  }

  private CallToolResult invokeTool(
      String name, CallToolHandler handler, JsonNode args, CallToolRequestParams params) {
    McpTransport transport = McpTransport.CURRENT.isBound() ? McpTransport.CURRENT.get() : null;
    ValueNode progressToken = params.meta() != null ? params.meta().progressToken() : null;
    DefaultMcpToolContext ctx =
        new DefaultMcpToolContext(
            transport, objectMapper, progressToken, correlationService, this, name);

    try {
      Object result = ScopedValue.where(McpToolContext.CURRENT, ctx).call(() -> handler.call(args));
      return toCallToolResult(result);
    } catch (JsonRpcException e) {
      // Guard denials (-32003) are a protocol-level gate and must surface as JSON-RPC errors, not
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

  CallToolResult toCallToolResult(Object result) {
    return switch (result) {
      case null -> new CallToolResult(List.of(new TextContent("", null)), null, null);
      case CallToolResult ctr -> ctr;
      case JsonNode node -> toCallToolResult(node);
      default -> toCallToolResult(objectMapper.valueToTree(result));
    };
  }

  private static CallToolResult toCallToolResult(JsonNode node) {
    ObjectNode structuredContent = node instanceof ObjectNode obj ? obj : null;
    return new CallToolResult(
        List.of(new TextContent(node.toString(), null)), null, structuredContent);
  }

  static CallToolResult toErrorCallToolResult(Throwable throwable) {
    String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    return new CallToolResult(List.of(new TextContent(message, null)), true, null);
  }
}
