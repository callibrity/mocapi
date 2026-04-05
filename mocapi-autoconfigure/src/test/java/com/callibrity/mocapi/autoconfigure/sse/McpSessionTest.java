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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpSessionTest {

  @Test
  void nextEventIdShouldContainStreamIdAndIncrement() {
    var session = new McpSession();
    String id1 = session.nextEventId("stream-A");
    String id2 = session.nextEventId("stream-A");

    assertThat(id1).startsWith("stream-A:");
    assertThat(id2).startsWith("stream-A:");
    assertThat(id1).isNotEqualTo(id2);
  }

  @Test
  void storeEventAndGetEventsAfterShouldRoundTrip() {
    var session = new McpSession();
    String streamId = "s1";

    String eventId1 = session.nextEventId(streamId);
    session.storeEvent(streamId, new SseEvent(eventId1, "data-1"));
    String eventId2 = session.nextEventId(streamId);
    session.storeEvent(streamId, new SseEvent(eventId2, "data-2"));
    String eventId3 = session.nextEventId(streamId);
    session.storeEvent(streamId, new SseEvent(eventId3, "data-3"));

    var replayed = session.getEventsAfter(eventId1);
    assertThat(replayed).hasSize(2);
    var iterator = replayed.iterator();
    assertThat(iterator.next().data()).isEqualTo("data-2");
    assertThat(iterator.next().data()).isEqualTo("data-3");
  }

  @Test
  void getEventsAfterShouldReturnEmptyForUnknownStream() {
    var session = new McpSession();
    var replayed = session.getEventsAfter("nonexistent-stream:1");
    assertThat(replayed).isEmpty();
  }

  @Test
  void getEventsAfterShouldReturnEmptyForNullStreamIdExtraction() {
    var session = new McpSession();
    assertThat(session.getEventsAfter("no-colon")).isEmpty();
  }

  @Test
  void getEventsAfterShouldReturnEmptyForNullEventId() {
    var session = new McpSession();
    assertThat(session.getEventsAfter(null)).isEmpty();
  }

  @Test
  void getEventsAfterLastEventShouldReturnEmpty() {
    var session = new McpSession();
    String streamId = "s1";
    String eventId = session.nextEventId(streamId);
    session.storeEvent(streamId, new SseEvent(eventId, "data"));

    var replayed = session.getEventsAfter(eventId);
    assertThat(replayed).isEmpty();
  }

  @Test
  void clearStreamShouldRemoveEvents() {
    var session = new McpSession();
    String streamId = "s1";
    String eventId = session.nextEventId(streamId);
    session.storeEvent(streamId, new SseEvent(eventId, "data"));

    session.clearStream(streamId);

    assertThat(session.getStreamEvents()).doesNotContainKey(streamId);
  }

  @Test
  void isInactiveShouldReturnFalseForActiveSession() {
    var session = new McpSession();
    assertThat(session.isInactive(3600)).isFalse();
  }

  @Test
  void isInactiveShouldReturnTrueForExpiredSession() throws Exception {
    var session = new McpSession();
    Thread.sleep(50);
    assertThat(session.isInactive(0)).isTrue();
  }

  @Test
  void sendNotificationShouldDeliverToEmitters() {
    var session = new McpSession();
    var emitter1 = new McpStreamEmitter(session);
    var emitter2 = new McpStreamEmitter(session);

    session.registerNotificationEmitter(emitter1);
    session.registerNotificationEmitter(emitter2);

    session.sendNotification("hello");

    assertThat(session.getNotificationEmitterCount()).isEqualTo(2);
  }

  @Test
  void sendNotificationShouldRemoveFailedEmitters() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    session.registerNotificationEmitter(emitter);
    assertThat(session.getNotificationEmitterCount()).isEqualTo(1);

    // Complete the underlying SseEmitter to make send() fail
    emitter.getEmitter().complete();
    session.sendNotification("hello");

    assertThat(session.getNotificationEmitterCount()).isZero();
  }

  @Test
  void extractStreamIdShouldParseCorrectly() {
    assertThat(McpSession.extractStreamId("abc-123:42")).isEqualTo("abc-123");
    assertThat(McpSession.extractStreamId("stream:with:colons:5")).isEqualTo("stream:with:colons");
    assertThat(McpSession.extractStreamId(null)).isNull();
    assertThat(McpSession.extractStreamId("nocolon")).isNull();
    assertThat(McpSession.extractStreamId(":1")).isNull();
  }

  @Test
  void sessionShouldHaveUniqueId() {
    var session1 = new McpSession();
    var session2 = new McpSession();
    assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
  }

  @Test
  void sessionShouldTrackCreatedAt() {
    var session = new McpSession();
    assertThat(session.getCreatedAt()).isNotNull();
  }

  @Test
  void registerNotificationEmitterShouldAutoRemoveOnClose() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    session.registerNotificationEmitter(emitter);
    assertThat(session.getNotificationEmitterCount()).isEqualTo(1);

    emitter.complete();
    assertThat(session.getNotificationEmitterCount()).isZero();
  }
}
