package com.callibrity.mocapi.web.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpError(int code, String message, JsonNode data) {

}
