package com.callibrity.mocapi.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface McpTool {
    String name();
    String title();
    String description();
    ObjectNode inputSchema();
    ObjectNode outputSchema();
    boolean isEnabledFor(String userId);
    ObjectNode call(ObjectNode parameters);
}
