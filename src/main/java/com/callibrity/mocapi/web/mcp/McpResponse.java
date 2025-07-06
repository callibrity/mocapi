package com.callibrity.mocapi.web.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpResponse(String jsonrpc, JsonNode result, McpError error, JsonNode id) {
}
