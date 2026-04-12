package com.callibrity.mocapi;

import com.callibrity.ripcurl.core.JsonRpcMessage;

public interface McpTransport {
    void emit(McpLifecycleEvent event);
    void send(JsonRpcMessage message);
}
