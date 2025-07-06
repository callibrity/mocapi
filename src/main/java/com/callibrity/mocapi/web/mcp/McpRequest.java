package com.callibrity.mocapi.web.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotEmpty;

public record McpRequest(
        @NotEmpty
        String jsonrpc,
        @NotEmpty
        String method,

        ObjectNode params,
        JsonNode id) {
}
