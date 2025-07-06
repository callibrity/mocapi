package com.callibrity.mocapi.mcp.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListToolsResponse(List<ToolDescriptor> tools, String nextCursor) {
}
