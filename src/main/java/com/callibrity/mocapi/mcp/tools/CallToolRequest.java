package com.callibrity.mocapi.mcp.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record CallToolRequest(String name, ObjectNode arguments) {
}
