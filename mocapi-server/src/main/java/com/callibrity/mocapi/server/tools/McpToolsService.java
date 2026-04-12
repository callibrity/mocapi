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

import static com.callibrity.mocapi.model.McpMethods.TOOLS_LIST;
import static java.util.Optional.ofNullable;

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpTransport;
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
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/** Manages tool registration, lookup, input validation, pagination, and JSON-RPC dispatch. */
@Slf4j
@JsonRpcService
public class McpToolsService {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, McpTool> tools;
  private final List<Tool> sortedDescriptors;
  private final ConcurrentHashMap<String, Schema> inputSchemas = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final McpResponseCorrelationService correlationService;
  private final int pageSize;

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
    var allTools =
        toolProviders.stream().flatMap(provider -> provider.getMcpTools().stream()).toList();
    this.tools = allTools.stream().collect(Collectors.toMap(t -> t.descriptor().name(), t -> t));
    this.sortedDescriptors =
        allTools.stream()
            .map(McpTool::descriptor)
            .sorted(Comparator.comparing(Tool::name))
            .toList();
    this.objectMapper = objectMapper;
    this.correlationService = correlationService;
    this.pageSize = pageSize;
  }

  @JsonRpcMethod(TOOLS_LIST)
  public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
    var page = paginate(sortedDescriptors, params);
    return new ListToolsResult(page.items(), page.nextCursor());
  }

  @JsonRpcMethod("tools/call")
  public CallToolResult callTool(@JsonRpcParams CallToolRequestParams params) {
    String name = params.name();
    JsonNode args =
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode();
    McpTool tool = lookup(name);
    validateInput(name, args, tool);
    return invokeTool(name, tool, args, params);
  }

  public McpTool lookup(String name) {
    return ofNullable(tools.get(name))
        .orElseThrow(
            () ->
                new JsonRpcException(
                    JsonRpcProtocol.INVALID_PARAMS, String.format("Tool %s not found.", name)));
  }

  public boolean isEmpty() {
    return tools.isEmpty();
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
    } catch (JsonRpcException e) {
      throw e;
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

  private <T> Page<T> paginate(List<T> all, PaginatedRequestParams params) {
    String cursor = params == null ? null : params.cursor();
    int offset = Math.clamp(decodeCursor(cursor), 0, all.size());
    int end = Math.min(offset + pageSize, all.size());
    List<T> page = List.copyOf(all.subList(offset, end));
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new Page<>(page, nextCursor);
  }

  private static String encodeCursor(int offset) {
    return Base64.getEncoder().encodeToString(ByteBuffer.allocate(4).putInt(offset).array());
  }

  private static int decodeCursor(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return ByteBuffer.wrap(Base64.getDecoder().decode(cursor)).getInt();
    } catch (Exception _) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
  }

  private record Page<T>(List<T> items, String nextCursor) {}
}
