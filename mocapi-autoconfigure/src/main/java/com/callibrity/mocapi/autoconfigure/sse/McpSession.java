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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

/**
 * Represents an MCP session for managing SSE streams and event delivery.
 *
 * <p>Each session has a unique ID and tracks:
 *
 * <ul>
 *   <li>Event IDs for stream resumability
 *   <li>Pending events per stream for redelivery
 *   <li>Session creation time
 *   <li>Last activity timestamp
 * </ul>
 *
 * <p>Thread-safe for concurrent access from multiple streams and request handlers.
 *
 * @see McpSessionManager
 */
@Getter
public class McpSession {

  // ------------------------------ FIELDS ------------------------------

  private final String sessionId;
  private final Instant createdAt;
  private final AtomicLong eventIdCounter = new AtomicLong(0);
  private final Map<String, Queue<SseEvent>> streamEvents = new ConcurrentHashMap<>();
  private final List<McpStreamEmitter> notificationEmitters = new CopyOnWriteArrayList<>();
  private volatile Instant lastActivity;

  // --------------------------- CONSTRUCTORS ---------------------------

  /** Creates a new MCP session with a cryptographically secure UUID. */
  public McpSession() {
    this.sessionId = UUID.randomUUID().toString();
    this.createdAt = Instant.now();
    this.lastActivity = Instant.now();
  }

  // -------------------------- OTHER METHODS --------------------------

  /**
   * Generates the next unique event ID for this session. Event IDs are formatted as {@code
   * streamId:counter} so the server can correlate a Last-Event-ID to the originating stream.
   *
   * @param streamId the stream identifier to encode in the event ID
   * @return the next event ID
   */
  public String nextEventId(String streamId) {
    updateActivity();
    return streamId + ":" + eventIdCounter.incrementAndGet();
  }

  /**
   * Stores an event for potential redelivery on a specific stream.
   *
   * @param streamId the stream identifier
   * @param event the event to store
   */
  public void storeEvent(String streamId, SseEvent event) {
    updateActivity();
    streamEvents.computeIfAbsent(streamId, k -> new ConcurrentLinkedQueue<>()).add(event);
  }

  /**
   * Extracts the stream ID from an event ID. Event IDs are formatted as {@code streamId:counter}.
   *
   * @param eventId the event ID to extract from
   * @return the stream ID, or null if the format is invalid
   */
  public static String extractStreamId(String eventId) {
    if (eventId == null) {
      return null;
    }
    int lastColon = eventId.lastIndexOf(':');
    if (lastColon <= 0) {
      return null;
    }
    return eventId.substring(0, lastColon);
  }

  /**
   * Retrieves events after a specific event ID for stream resumption. The original stream ID is
   * extracted from the Last-Event-ID, ensuring events are only replayed from the originating
   * stream.
   *
   * @param lastEventId the last event ID received by the client
   * @return events after the specified ID, empty if stream not found
   */
  public Collection<SseEvent> getEventsAfter(String lastEventId) {
    updateActivity();
    String originalStreamId = extractStreamId(lastEventId);
    if (originalStreamId == null) {
      return List.of();
    }

    Queue<SseEvent> events = streamEvents.get(originalStreamId);
    if (events == null) {
      return List.of();
    }

    Queue<SseEvent> replayEvents = new ConcurrentLinkedQueue<>();
    boolean foundLast = false;

    for (SseEvent event : events) {
      if (foundLast) {
        replayEvents.add(event);
      } else if (event.id().equals(lastEventId)) {
        foundLast = true;
      }
    }

    return replayEvents;
  }

  /**
   * Removes all events for a specific stream (called when stream completes).
   *
   * @param streamId the stream identifier
   */
  public void clearStream(String streamId) {
    updateActivity();
    streamEvents.remove(streamId);
  }

  /**
   * Registers a notification emitter for receiving server-initiated messages. The emitter is
   * automatically removed when it completes or encounters an error.
   *
   * @param emitter the emitter to register
   */
  public void registerNotificationEmitter(McpStreamEmitter emitter) {
    updateActivity();
    notificationEmitters.add(emitter);
    emitter.onClose(() -> notificationEmitters.remove(emitter));
  }

  /**
   * Sends a notification to all registered notification emitters. Emitters that fail to send are
   * silently removed.
   *
   * @param notification the notification payload to send
   */
  public void sendNotification(Object notification) {
    updateActivity();
    for (McpStreamEmitter emitter : notificationEmitters) {
      try {
        emitter.send(notification);
      } catch (Exception _) {
        notificationEmitters.remove(emitter);
      }
    }
  }

  /**
   * Returns the number of currently registered notification emitters.
   *
   * @return the count of active notification emitters
   */
  public int getNotificationEmitterCount() {
    return notificationEmitters.size();
  }

  /** Updates the last activity timestamp for session timeout tracking. */
  private void updateActivity() {
    this.lastActivity = Instant.now();
  }

  /**
   * Checks if the session has been inactive for longer than the specified duration.
   *
   * @param inactiveSeconds the number of seconds of inactivity to check
   * @return true if the session has been inactive for longer than the specified duration
   */
  public boolean isInactive(long inactiveSeconds) {
    return Instant.now().isAfter(lastActivity.plusSeconds(inactiveSeconds));
  }
}
