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
package com.callibrity.mocapi.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.LoggingLevel;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.odyssey.core.StreamSubscriberBuilder;

class McpSessionServiceTest {

  private static final Duration TTL = Duration.ofHours(1);

  private McpSessionStore store;
  private McpSessionService service;
  private byte[] masterKey;
  private OdysseyStreamRegistry streamRegistry;

  @BeforeEach
  void setUp() {
    store = mock(McpSessionStore.class);
    streamRegistry = mock(OdysseyStreamRegistry.class);
    OdysseyStream channelStream = mock(OdysseyStream.class);
    when(streamRegistry.channel(any())).thenReturn(channelStream);
    masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    service = new McpSessionService(store, masterKey, TTL, streamRegistry);
  }

  @Test
  void constructorRejectsInvalidKey() {
    assertThatThrownBy(() -> new McpSessionService(store, new byte[16], TTL, streamRegistry))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createGeneratesIdAndDelegatesToStore() {
    McpSession session = new McpSession("2025-11-25", null, null);

    String id = service.create(session);

    assertThat(id).isNotNull().isNotEmpty();
    verify(store).save(any(McpSession.class), eq(TTL));
  }

  @Test
  void findReturnSessionAndTouchesOnHit() {
    McpSession session = new McpSession("2025-11-25", null, null);
    when(store.find("sess-123")).thenReturn(Optional.of(session));

    Optional<McpSession> result = service.find("sess-123");

    assertThat(result).contains(session);
    verify(store).touch("sess-123", TTL);
  }

  @Test
  void findReturnsEmptyAndDoesNotTouchOnMiss() {
    when(store.find("sess-missing")).thenReturn(Optional.empty());

    Optional<McpSession> result = service.find("sess-missing");

    assertThat(result).isEmpty();
    verify(store, never()).touch(any(), any());
  }

  @Test
  void deleteDelegatesToStoreAndCleansUpStream() {
    OdysseyStream channelStream = mock(OdysseyStream.class);
    when(streamRegistry.channel("sess-123")).thenReturn(channelStream);

    service.delete("sess-123");

    verify(store).delete("sess-123");
    verify(channelStream).delete();
  }

  @Test
  void setLogLevelUpdatesSession() {
    McpSession session = new McpSession("2025-11-25", null, null);
    when(store.find("sess-123")).thenReturn(Optional.of(session));

    service.setLogLevel("sess-123", LoggingLevel.DEBUG);

    verify(store).update(eq("sess-123"), eq(session.withLogLevel(LoggingLevel.DEBUG)));
  }

  @Test
  void setLogLevelThrowsWhenSessionNotFound() {
    when(store.find("sess-missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setLogLevel("sess-missing", LoggingLevel.DEBUG))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Session not found");
  }

  @Test
  void encryptDecryptRoundTrip() {
    String plaintext = "elicit:550e8400-e29b-41d4-a716-446655440000";
    String sessionId = "sess-abc-123";

    String encrypted = service.encrypt(sessionId, plaintext);
    String decrypted = service.decrypt(sessionId, encrypted);

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void decryptWithWrongSessionIdThrows() {
    String encrypted = service.encrypt("sess-1", "data");

    assertThatThrownBy(() -> service.decrypt("sess-2", encrypted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid or tampered");
  }

  @Test
  void decryptMalformedBase64Throws() {
    assertThatThrownBy(() -> service.decrypt("sess-1", "not!valid!base64!!!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Malformed Base64");
  }

  @Test
  void encryptProducesBase64Output() {
    String encrypted = service.encrypt("sess-1", "plaintext");
    assertThat(encrypted).matches("[A-Za-z0-9+/=]+");
  }

  @Test
  void differentSessionsProduceDifferentTokens() {
    String encrypted1 = service.encrypt("sess-A", "same-data");
    String encrypted2 = service.encrypt("sess-B", "same-data");
    assertThat(encrypted1).isNotEqualTo(encrypted2);
  }

  @Test
  void createStreamDelegatesToRegistry() {
    OdysseyStream ephemeral = mock(OdysseyStream.class);
    when(streamRegistry.ephemeral()).thenReturn(ephemeral);

    McpSessionStream result = service.createStream("sess-123");

    assertThat(result).isNotNull();
    verify(streamRegistry).ephemeral();
  }

  @Test
  void notificationStreamReturnsSessionStream() {
    OdysseyStream channel = mock(OdysseyStream.class);
    when(streamRegistry.channel("sess-123")).thenReturn(channel);

    McpSessionStream result = service.notificationStream("sess-123");

    assertThat(result).isNotNull();
    verify(streamRegistry).channel("sess-123");
  }

  @Test
  void reconnectStreamDecryptsAndResumesAfter() {
    String sessionId = "sess-123";
    String streamKey = "odyssey:ephemeral:abc";
    String rawEventId = "event-456";
    String encrypted = service.encrypt(sessionId, streamKey + ":" + rawEventId);

    OdysseyStream stream = mock(OdysseyStream.class);
    StreamSubscriberBuilder builder = mock(StreamSubscriberBuilder.class);
    when(builder.mapper(any())).thenReturn(builder);
    when(builder.resumeAfter(rawEventId))
        .thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());
    when(stream.subscriber()).thenReturn(builder);
    when(streamRegistry.stream(streamKey)).thenReturn(stream);

    var emitter = service.reconnectStream(sessionId, encrypted);

    assertThat(emitter).isNotNull();
    verify(streamRegistry).stream(streamKey);
    verify(builder).resumeAfter(rawEventId);
  }

  @Test
  void reconnectStreamRejectsTamperedToken() {
    assertThatThrownBy(() -> service.reconnectStream("sess-123", "tampered-garbage"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconnectStreamRejectsWrongSession() {
    String encrypted = service.encrypt("sess-A", "key:id");

    assertThatThrownBy(() -> service.reconnectStream("sess-B", encrypted))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
