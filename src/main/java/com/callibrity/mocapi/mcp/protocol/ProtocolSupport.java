package com.callibrity.mocapi.mcp.protocol;

import com.callibrity.mocapi.jsonrpc.method.annotation.JsonRpc;
import org.springframework.stereotype.Component;

@Component
public class ProtocolSupport {

// ------------------------------ FIELDS ------------------------------

    public static final String PROTOCOL_VERSION = "2025-06-18";

// -------------------------- OTHER METHODS --------------------------

    @JsonRpc("initialize")
    public InitializeResponse initialize(String protocolVersion,
                                         ClientCapabilities capabilities,
                                         ClientInfo clientInfo) {
        return new InitializeResponse(PROTOCOL_VERSION,
                new ServerCapabilities(new ToolsCapability(false)),
                new ServerInfo("Mocapi", "Mocapi", "1.0.0"),
                "");
    }

    @JsonRpc("ping")
    public PingResponse ping() {
        return new PingResponse();
    }

    @JsonRpc("notifications/initialized")
    public void clientInitialized() {
        // Do nothing!
    }
}
