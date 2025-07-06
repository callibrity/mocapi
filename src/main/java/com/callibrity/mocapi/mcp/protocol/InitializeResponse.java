package com.callibrity.mocapi.mcp.protocol;

public record InitializeResponse(
        String protocolVersion,
        ServerCapabilities capabilities,
        ServerInfo serverInfo,
        String instructions
) {
}
