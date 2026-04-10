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

import static java.util.Optional.ofNullable;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class ToolsRegistry {

  // ------------------------------ FIELDS ------------------------------

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, McpTool> tools;
  private final ConcurrentHashMap<String, Schema> inputSchemas = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final int pageSize;

  // --------------------------- CONSTRUCTORS ---------------------------

  public ToolsRegistry(List<McpToolProvider> toolProviders, ObjectMapper objectMapper) {
    this(toolProviders, objectMapper, DEFAULT_PAGE_SIZE);
  }

  public ToolsRegistry(
      List<McpToolProvider> toolProviders, ObjectMapper objectMapper, int pageSize) {
    this.tools =
        toolProviders.stream()
            .flatMap(provider -> provider.getMcpTools().stream())
            .collect(Collectors.toMap(McpTool::name, t -> t));
    this.objectMapper = objectMapper;
    this.pageSize = pageSize;
  }

  // -------------------------- OTHER METHODS --------------------------

  public McpTool lookup(String name) {
    return ofNullable(tools.get(name))
        .orElseThrow(
            () ->
                new JsonRpcException(
                    JsonRpcProtocol.INVALID_PARAMS, String.format("Tool %s not found.", name)));
  }

  public CallToolResponse callTool(String name, JsonNode arguments) {
    var tool = lookup(name);
    validateInput(name, arguments, tool);
    var result = tool.call(arguments);
    if (result instanceof CallToolResponse response) {
      return response;
    }
    JsonNode jsonResult = result != null ? objectMapper.valueToTree(result) : null;
    ObjectNode structuredContent =
        jsonResult != null && jsonResult.isObject() ? (ObjectNode) jsonResult : null;
    String text = jsonResult != null ? jsonResult.toString() : "";
    var textContent = new TextContent(text);
    return new CallToolResponse(List.of(textContent), null, structuredContent);
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
        name, _ -> new SchemaLoader(new JsonParser(tool.inputSchema().toString()).parse()).load());
  }

  public boolean isEmpty() {
    return tools.isEmpty();
  }

  public ListToolsResponse listTools(String cursor) {
    var allDescriptors =
        tools.values().stream()
            .map(
                t ->
                    new McpToolDescriptor(
                        t.name(), t.title(), t.description(), t.inputSchema(), t.outputSchema()))
            .sorted(Comparator.comparing(McpToolDescriptor::name))
            .toList();

    int offset = decodeCursor(cursor);
    if (offset < 0 || offset > allDescriptors.size()) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }

    int end = Math.min(offset + pageSize, allDescriptors.size());
    var page = allDescriptors.subList(offset, end);
    String nextCursor = end < allDescriptors.size() ? encodeCursor(end) : null;
    return new ListToolsResponse(page, nextCursor);
  }

  static String encodeCursor(int offset) {
    return Base64.getEncoder()
        .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
  }

  static int decodeCursor(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return Integer.parseInt(
          new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException _) {
      return -1;
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = AudioContent.class, name = "audio"),
    @JsonSubTypes.Type(value = ResourceContent.class, name = "resource")
  })
  public sealed interface Content
      permits TextContent, ImageContent, AudioContent, ResourceContent {}

  public record TextContent(String text) implements Content {}

  public record ImageContent(String data, String mimeType) implements Content {}

  public record AudioContent(String data, String mimeType) implements Content {}

  public record ResourceContent(EmbeddedResource resource) implements Content {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record EmbeddedResource(String uri, String mimeType, String text, String blob) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CallToolResponse(
      List<Content> content, Boolean isError, ObjectNode structuredContent) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ListToolsResponse(List<McpToolDescriptor> tools, String nextCursor) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record McpToolDescriptor(
      String name,
      String title,
      String description,
      ObjectNode inputSchema,
      ObjectNode outputSchema) {}
}
