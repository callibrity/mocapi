package com.callibrity.mocapi.mcp.tools;

import com.callibrity.mocapi.jsonrpc.method.annotation.JsonRpc;
import com.callibrity.mocapi.mcp.McpTool;
import com.callibrity.mocapi.mcp.McpToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ToolsSupport {

    private final Map<String,McpTool> tools;

    public ToolsSupport(List<McpToolProvider> providers) {
        this.tools = providers.stream()
                .flatMap(provider -> provider.getTools().stream())
                .collect(Collectors.toMap(McpTool::name, t -> t));
    }

    @JsonRpc("tools/list")
    public ListToolsResponse listTools(String cursor) {
        var descriptors = tools.values().stream()
                .map(t -> new ToolDescriptor(t.name(), t.title(), t.description(), t.inputSchema(), t.outputSchema()))
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
        return new ListToolsResponse(descriptors, null);
    }

    @JsonRpc("tools/call")
    public CallToolResponse callTool(String name, ObjectNode arguments) {
        var tool = lookupTool(name);
        var structuredOutput = tool.call(arguments);
        return new CallToolResponse(structuredOutput);
    }

    private McpTool lookupTool(String name) {
        return tools.get(name);
    }
}
