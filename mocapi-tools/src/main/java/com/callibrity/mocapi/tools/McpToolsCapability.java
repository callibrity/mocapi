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

import com.callibrity.mocapi.server.McpServerCapability;
import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.callibrity.ripcurl.core.util.LazyInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@JsonRpcService
public class McpToolsCapability implements McpServerCapability {

// ------------------------------ FIELDS ------------------------------

    private final LazyInitializer<Map<String,McpTool>> tools;
    private final ConcurrentHashMap<String, Schema> inputSchemas = new ConcurrentHashMap<>();

// --------------------------- CONSTRUCTORS ---------------------------

    public McpToolsCapability(List<McpToolProvider> toolProviders) {
        this.tools = LazyInitializer.of(() -> toolProviders.stream()
                .flatMap(provider -> provider.getMcpTools().stream())
                .collect(Collectors.toMap(McpTool::name, t -> t)));
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

    @JsonRpc("tools/call")
    public CallToolResponse callTool(String name, ObjectNode arguments) {
        var tool = lookupTool(name);
        validateInput(name, arguments, tool);
        var structuredOutput = tool.call(arguments);
        return new CallToolResponse(structuredOutput);
    }

    private McpTool lookupTool(String name) {
        return ofNullable(tools.get().get(name)).orElseThrow(() -> new JsonRpcInvalidParamsException(String.format("Tool %s not found.", name)));
    }

    private void validateInput(String name, ObjectNode arguments, McpTool tool) {
        try {
            getInputSchema(name, tool).validate(convertObjectNodeToJSONObject(arguments));
        } catch (ValidationException e) {
            throw new JsonRpcInvalidParamsException(e.getMessage());
        }
    }

    private Schema getInputSchema(String name, McpTool tool) {
        return inputSchemas.computeIfAbsent(name, _ -> SchemaLoader.load(new JSONObject(tool.inputSchema().toString())));
    }

    private JSONObject convertObjectNodeToJSONObject(ObjectNode objectNode) {
        JSONObject jsonObject = new JSONObject();
        objectNode.fields().forEachRemaining(entry -> {
            jsonObject.put(entry.getKey(), convertJsonNodeValue(entry.getValue()));
        });
        return jsonObject;
    }

    private Object convertJsonNodeValue(JsonNode node) {
        if (node.isNull()) {
            return JSONObject.NULL;
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isInt()) {
            return node.intValue();
        } else if (node.isLong()) {
            return node.longValue();
        } else if (node.isDouble()) {
            return node.doubleValue();
        } else if (node.isTextual()) {
            return node.textValue();
        } else if (node.isArray()) {
            JSONArray array = new JSONArray();
            node.elements().forEachRemaining(element -> array.put(convertJsonNodeValue(element)));
            return array;
        } else if (node.isObject()) {
            return convertObjectNodeToJSONObject((ObjectNode) node);
        } else {
            return node.toString();
        }
    }

    @JsonRpc("tools/list")
    public ListToolsResponse listTools(String cursor) {
        var descriptors = tools.get().values().stream()
                .map(t -> new McpToolDescriptor(t.name(), t.title(), t.description(), t.inputSchema(), t.outputSchema()))
                .sorted(Comparator.comparing(McpToolDescriptor::name))
                .toList();
        return new ListToolsResponse(descriptors, null);
    }

// -------------------------- INNER CLASSES --------------------------

    public record ToolsCapabilityDescriptor(boolean listChanged) {
    }

    public record CallToolResponse(ObjectNode structuredContent) {
    }

    public record ListToolsResponse(List<McpToolDescriptor> tools, String nextCursor) {
    }

    public record McpToolDescriptor(String name, String title, String description, ObjectNode inputSchema, ObjectNode outputSchema) {
    }
}
