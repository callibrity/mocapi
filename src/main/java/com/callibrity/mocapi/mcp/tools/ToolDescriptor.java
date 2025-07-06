package com.callibrity.mocapi.mcp.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record ToolDescriptor(String name, String title, String description, ObjectNode inputSchema, ObjectNode outputSchema) {
}
