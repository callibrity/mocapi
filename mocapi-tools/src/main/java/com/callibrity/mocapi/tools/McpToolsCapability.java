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

import com.callibrity.mocapi.server.CapabilityDescriptor;
import com.callibrity.mocapi.server.McpServerCapability;
import com.callibrity.mocapi.server.exception.McpInvalidParamsException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import tools.jackson.databind.node.ObjectNode;

public class McpToolsCapability implements McpServerCapability {

  // ------------------------------ FIELDS ------------------------------

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, McpTool> tools;
  private final ConcurrentHashMap<String, Schema> inputSchemas = new ConcurrentHashMap<>();
  private final int pageSize;

  // --------------------------- CONSTRUCTORS ---------------------------

  public McpToolsCapability(List<McpToolProvider> toolProviders) {
    this(toolProviders, DEFAULT_PAGE_SIZE);
  }

  public McpToolsCapability(List<McpToolProvider> toolProviders, int pageSize) {
    this.tools =
        toolProviders.stream()
            .flatMap(provider -> provider.getMcpTools().stream())
            .collect(Collectors.toMap(McpTool::name, t -> t));
    this.pageSize = pageSize;
  }

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface McpServerCapability ---------------------

  @Override
  public String name() {
    return "tools";
  }

  @Override
  public ToolsCapabilityDescriptor describe() {
    return new ToolsCapabilityDescriptor(false);
  }

  // -------------------------- OTHER METHODS --------------------------

  public CallToolResponse callTool(String name, ObjectNode arguments) {
    var tool = lookupTool(name);
    validateInput(name, arguments, tool);
    var structuredOutput = tool.call(arguments);
    return new CallToolResponse(structuredOutput);
  }

  private McpTool lookupTool(String name) {
    return ofNullable(tools.get(name))
        .orElseThrow(
            () -> new McpInvalidParamsException(String.format("Tool %s not found.", name)));
  }

  private void validateInput(String name, ObjectNode arguments, McpTool tool) {
    try {
      getInputSchema(name, tool).validate(new JSONObject(arguments.toString()));
    } catch (ValidationException e) {
      throw new McpInvalidParamsException(e.getMessage());
    }
  }

  private Schema getInputSchema(String name, McpTool tool) {
    return inputSchemas.computeIfAbsent(
        name, _ -> SchemaLoader.load(new JSONObject(tool.inputSchema().toString())));
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
      throw new McpInvalidParamsException("Invalid cursor");
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
    } catch (IllegalArgumentException e) {
      return -1;
    }
  }

  // -------------------------- INNER CLASSES --------------------------

  public record ToolsCapabilityDescriptor(boolean listChanged) implements CapabilityDescriptor {}

  public record CallToolResponse(ObjectNode structuredContent) {}

  public record ListToolsResponse(List<McpToolDescriptor> tools, String nextCursor) {}

  public record McpToolDescriptor(
      String name,
      String title,
      String description,
      ObjectNode inputSchema,
      ObjectNode outputSchema) {}
}
