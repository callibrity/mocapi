package com.callibrity.mocapi;

import com.callibrity.ripcurl.core.JsonRpcMessage;

public interface McpProtocol {
    void handle(McpContext context, JsonRpcMessage message, McpTransport transport);
}
