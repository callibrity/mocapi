package com.callibrity.mocapi;

public sealed interface McpLifecycleEvent {

    record SessionInitialized(String sessionId) implements McpLifecycleEvent{
    }
}
