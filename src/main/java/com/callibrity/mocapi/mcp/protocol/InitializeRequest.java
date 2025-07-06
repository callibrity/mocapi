package com.callibrity.mocapi.mcp.protocol;

public record InitializeRequest(
        String protocolVersion,
        ClientCapabilities capabilities,
        ClientInfo clientInfo
) {
}
