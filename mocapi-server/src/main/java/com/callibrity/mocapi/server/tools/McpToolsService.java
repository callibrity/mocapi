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

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.ServerCapabilitiesBuilder;
import com.callibrity.mocapi.server.ServerCapabilitiesContributor;
import com.callibrity.mocapi.server.util.PaginatedService;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/** Manages tool registration, input validation, and JSON-RPC dispatch. */
@Slf4j
@JsonRpcService
public class McpToolsService extends PaginatedService<McpTool, Tool>
    implements ServerCapabilitiesContributor {

  private final ConcurrentHashMap<String, Schema> inputSchemas = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final McpResponseCorrelationService correlationService;

  public McpToolsService(
      List<McpToolProvider> toolProviders,
      ObjectMapper objectMapper,
      McpResponseCorrelationService correlationService) {
    this(toolProviders, objectMapper, correlationService, DEFAULT_PAGE_SIZE);
  }

  public McpToolsService(
      List<McpToolProvider> toolProviders,
      ObjectMapper objectMapper,
      McpResponseCorrelationService correlationService,
      int pageSize) {
    super(
        toolProviders.stream().flatMap(p -> p.getMcpTools().stream()).toList(),
        t -> t.descriptor().name(),
        McpTool::descriptor,
        Comparator.comparing(Tool::name),
        "Tool",
        pageSize);
    this.objectMapper = objectMapper;
    this.correlationService = correlationService;
  }

  @JsonRpcMethod(TOOLS_LIST)
  public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
    return paginate(params, ListToolsResult::new);
  }

  @JsonRpcMethod(TOOLS_CALL)
  public CallToolResult callTool(@JsonRpcParams CallToolRequestParams params) {
    String name = params.name();
    JsonNode args =
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode();
    McpTool tool = lookup(name);
    validateInput(name, args, tool);
    return invokeTool(name, tool, args, params);
  }

  @Override
  public void contribute(ServerCapabilitiesBuilder builder) {
    if (!isEmpty()) {
      builder.tools(new ToolsCapability(false));
    }
  }

  private CallToolResult invokeTool(
      String name, McpTool tool, JsonNode args, CallToolRequestParams params) {
    McpTransport transport = McpTransport.CURRENT.isBound() ? McpTransport.CURRENT.get() : null;
    ValueNode progressToken = params.meta() != null ? params.meta().progressToken() : null;
    DefaultMcpToolContext ctx =
        new DefaultMcpToolContext(transport, objectMapper, progressToken, correlationService);

    try {
      Object result = ScopedValue.where(McpToolContext.CURRENT, ctx).call(() -> tool.call(args));
      return toCallToolResult(result);
    } catch (Exception e) {
      log.warn("Tool {} threw an unhandled exception", name, e);
      return toErrorCallToolResult(e);
    }
  }

  CallToolResult toCallToolResult(Object result) {
    if (result instanceof CallToolResult callToolResult) {
      return callToolResult;
    }
    JsonNode jsonResult = result != null ? objectMapper.valueToTree(result) : null;
    ObjectNode structuredContent =
        jsonResult != null && jsonResult.isObject() ? (ObjectNode) jsonResult : null;
    String text = jsonResult != null ? jsonResult.toString() : "";
    var textContent = new TextContent(text, null);
    return new CallToolResult(List.of(textContent), null, structuredContent);
  }

  static CallToolResult toErrorCallToolResult(Throwable throwable) {
    String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    return new CallToolResult(List.of(new TextContent(message, null)), true, null);
  }

  private void validateInput(String name, JsonNode arguments, McpTool tool) {
    Schema schema = getInputSchema(name, tool);
    Validator validator = Validator.forSchema(schema);
    ValidationFailure failure = validator.validate(new JsonParser(arguments.toString()).parse());
    if (failure != null) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, failure.getMessage());
    }
  }

  private Schema getInputSchema(String name, McpTool tool) {
    return inputSchemas.computeIfAbsent(
        name,
        _ ->
            new SchemaLoader(new JsonParser(tool.descriptor().inputSchema().toString()).parse())
                .load());
  }
}
