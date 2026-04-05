/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.autoconfigure.sse;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper around Spring's {@link SseEmitter} that provides enhanced logging, error handling,
 * and event ID management for MCP SSE streams.
 * <p>
 * This class is based on the fault-tolerant SSE pattern from calli-api's ChatCompletionEmitter,
 * adapted for MCP protocol streaming requirements.
 * <p>
 * Key features:
 * <ul>
 *   <li>Thread-safe completion handling with idempotency via {@link AtomicBoolean}</li>
 *   <li>Automatic error recovery and cleanup on client disconnects or network failures</li>
 *   <li>Event ID tracking for stream resumability per MCP 2025-11-25 spec</li>
 *   <li>Detailed logging for debugging SSE stream lifecycle</li>
 * </ul>
 *
 * @see SseEmitter
 * @see McpSession
 */
@Slf4j
public class McpStreamEmitter {

// ------------------------------ FIELDS ------------------------------

    private final String sessionId;
    @Getter
    private final String streamId;
    @Getter
    private final SseEmitter emitter;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final McpSession session;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Creates a new emitter with no timeout (connection can stay open indefinitely).
     *
     * @param session the MCP session managing this stream
     */
    public McpStreamEmitter(McpSession session) {
        this(session, Duration.ZERO);
    }

    /**
     * Creates a new emitter with the specified timeout.
     *
     * @param session the MCP session managing this stream
     * @param timeout the SSE connection timeout, or {@link Duration#ZERO} for no timeout
     */
    public McpStreamEmitter(McpSession session, Duration timeout) {
        this.session = session;
        this.sessionId = session.getSessionId();
        this.streamId = UUID.randomUUID().toString();
        this.emitter = new SseEmitter(timeout.isZero() ? 0L : timeout.toMillis());

        emitter.onCompletion(() -> {
            completed.set(true);
            session.clearStream(streamId);
            log.debug("[MCP-SSE:{}:{}] Stream completed", sessionId, streamId);
        });

        emitter.onTimeout(() -> {
            log.warn("[MCP-SSE:{}:{}] Stream timeout occurred", sessionId, streamId);
            completeWithError(new TimeoutException("SSE stream timeout"));
        });

        emitter.onError(err -> {
            log.warn("[MCP-SSE:{}:{}] Stream error: {}", sessionId, streamId, err.toString());
            completeWithError(err);
        });
    }

    /**
     * Static factory method to create an emitter with a timeout.
     *
     * @param session the MCP session managing this stream
     * @param timeout the SSE connection timeout, or {@link Duration#ZERO} for no timeout
     * @return a new {@link McpStreamEmitter} instance
     */
    public static McpStreamEmitter withTimeout(McpSession session, Duration timeout) {
        return new McpStreamEmitter(session, timeout);
    }

    // ------------------------------------------------------------
    //  COMPLETION + ERROR HANDLING
    // ------------------------------------------------------------

    /**
     * Completes the emitter with an error. This method is idempotent - multiple calls
     * will only complete the emitter once.
     *
     * @param ex the exception that caused the error
     */
    private void completeWithError(Throwable ex) {
        if (!completed.compareAndSet(false, true)) {
            log.trace("[MCP-SSE:{}:{}] completeWithError called but stream already closed",
                    sessionId, streamId);
            return;
        }

        log.debug("[MCP-SSE:{}:{}] Completing with error: {}", sessionId, streamId, ex.toString());
        session.clearStream(streamId);

        try {
            emitter.completeWithError(ex);
        } catch (IllegalStateException e) {
            log.trace("[MCP-SSE:{}:{}] completeWithError failed (already closed)",
                    sessionId, streamId);
        } catch (Exception t) {
            log.warn("[MCP-SSE:{}:{}] Unexpected error completing stream",
                    sessionId, streamId, t);
        }
    }

// -------------------------- OTHER METHODS --------------------------

    /**
     * Completes the SSE stream normally. This method is idempotent - multiple calls
     * will only complete the emitter once.
     */
    public void complete() {
        if (!completed.compareAndSet(false, true)) {
            log.trace("[MCP-SSE:{}:{}] complete() called but stream already closed",
                    sessionId, streamId);
            return;
        }

        log.debug("[MCP-SSE:{}:{}] Completing stream normally", sessionId, streamId);
        session.clearStream(streamId);

        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.trace("[MCP-SSE:{}:{}] Stream already closed during complete()",
                    sessionId, streamId);
        } catch (Exception t) {
            log.warn("[MCP-SSE:{}:{}] Unexpected error while completing stream",
                    sessionId, streamId, t);
        }
    }

    /**
     * Sends a priming event with an empty data field to establish the connection
     * and enable client reconnection via Last-Event-ID.
     * <p>
     * Per MCP 2025-11-25 spec: "Server SHOULD immediately send SSE event with event ID
     * and empty data field to prime client reconnection."
     */
    public void sendPrimingEvent() {
        String eventId = session.nextEventId();
        SseEvent event = new SseEvent(eventId, "");
        session.storeEvent(streamId, event);
        trySendInternal(event);
    }

    /**
     * Sends data as an SSE event with automatic event ID generation.
     * The event is stored in the session for potential redelivery.
     *
     * @param data the event data (typically a JSON-RPC message)
     */
    public void send(Object data) {
        String eventId = session.nextEventId();
        SseEvent event = new SseEvent(eventId, data);
        session.storeEvent(streamId, event);
        trySendInternal(event);
    }

    /**
     * Sends a final event and then completes the stream normally.
     * If the send fails, the stream will be completed with an error instead.
     *
     * @param data the final event data
     */
    public void sendAndComplete(Object data) {
        String eventId = session.nextEventId();
        SseEvent event = new SseEvent(eventId, data);
        session.storeEvent(streamId, event);

        if (trySendInternal(event)) {
            complete();
        }
    }

    /**
     * Centralized send logic with error handling and logging.
     *
     * @param event the event to send
     * @return {@code true} if the send was successful, {@code false} if it failed or the stream was already completed
     */
    private boolean trySendInternal(SseEvent event) {
        if (completed.get()) {
            log.trace("[MCP-SSE:{}:{}] Attempt to send event {} after completion ignored",
                    sessionId, streamId, event.id());
            return false;
        }

        try {
            emitter.send(SseEmitter.event()
                    .id(event.id())
                    .data(event.data()));
            return true;
        } catch (IOException e) {
            log.debug("[MCP-SSE:{}:{}] Client disconnected while sending event {}: {}",
                    sessionId, streamId, event.id(), e.toString());
            completeWithError(e);
            return false;
        } catch (Exception e) {
            log.error("[MCP-SSE:{}:{}] Unexpected exception while sending event {}",
                    sessionId, streamId, event.id(), e);
            completeWithError(e);
            return false;
        }
    }
}
