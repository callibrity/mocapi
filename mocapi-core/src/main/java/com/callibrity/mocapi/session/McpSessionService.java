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

import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.security.Ciphers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

/**
 * Single public API for all session concerns — lifecycle, encryption, lookup, and stream
 * management. The controller and stream context interact with sessions and streams through this
 * service only.
 */
public class McpSessionService {

  private final McpSessionStore store;
  private final byte[] masterKey;
  private final Duration ttl;
  private final Odyssey odyssey;

  public McpSessionService(McpSessionStore store, byte[] masterKey, Duration ttl, Odyssey odyssey) {
    Ciphers.validateAesGcmKey(masterKey);
    this.store = store;
    this.masterKey = masterKey.clone();
    this.ttl = ttl;
    this.odyssey = odyssey;
  }

  /** Generates a session ID, saves the session to the store, and returns the ID. */
  public String create(McpSession session) {
    String sessionId = UUID.randomUUID().toString();
    store.save(session.withSessionId(sessionId), ttl);
    return sessionId;
  }

  /**
   * Looks up a session by ID. If found, extends the TTL and returns it. If expired or missing,
   * returns empty.
   */
  public Optional<McpSession> find(String sessionId) {
    Optional<McpSession> result = store.find(sessionId);
    result.ifPresent(_ -> store.touch(sessionId, ttl));
    return result;
  }

  /** Removes a session and its notification channel from the store. */
  public void delete(String sessionId) {
    store.delete(sessionId);
    odyssey.publisher(sessionId, JsonNode.class).delete();
  }

  /**
   * Creates a new SSE stream bound to this session. The stream gets a random UUID name so multiple
   * concurrent streams for the same session don't collide. Event IDs are encrypted with
   * session-bound keys.
   *
   * @param sessionId the session that owns the stream
   * @return a new ephemeral stream with encrypted event IDs
   */
  public McpSessionStream createStream(String sessionId) {
    return new DefaultMcpSessionStream(
        odyssey, UUID.randomUUID().toString(), encryptingMapper(sessionId));
  }

  private SseEventMapper<JsonNode> encryptingMapper(String sessionId) {
    return event -> {
      String plaintext = event.streamName() + ":" + event.id();
      String encryptedId = encrypt(sessionId, plaintext);
      SseEmitter.SseEventBuilder builder =
          SseEmitter.event().id(encryptedId).data(event.data().toString());
      if (event.eventType() != null) {
        builder.name(event.eventType());
      }
      return builder;
    };
  }

  /**
   * Reconnects to an existing SSE stream. Decrypts {@code lastEventId} to find the stream name and
   * resume position.
   *
   * @param sessionId the session ID for decryption
   * @param lastEventId the encrypted Last-Event-ID from the client
   * @return an SSE emitter that replays missed events then continues live
   * @throws IllegalArgumentException if the token is tampered, from a wrong session, or malformed
   */
  public SseEmitter reconnectStream(String sessionId, String lastEventId) {
    String plaintext = decrypt(sessionId, lastEventId);
    int colonIndex = plaintext.lastIndexOf(':');
    if (colonIndex < 0) {
      throw new IllegalArgumentException("Invalid encrypted event ID format");
    }
    String streamName = plaintext.substring(0, colonIndex);
    String rawEventId = plaintext.substring(colonIndex + 1);
    return odyssey.resume(
        streamName, JsonNode.class, rawEventId, cfg -> cfg.mapper(encryptingMapper(sessionId)));
  }

  /**
   * Returns the session's notification channel as an {@link McpSessionStream}. The channel name is
   * the session ID, so every caller connecting to the same session's notifications shares one
   * underlying journal.
   *
   * @param sessionId the session whose notification channel to return
   * @return the notification channel wrapped as an {@link McpSessionStream}
   */
  public McpSessionStream notificationStream(String sessionId) {
    return new DefaultMcpSessionStream(odyssey, sessionId, encryptingMapper(sessionId));
  }

  /** Updates the log level for the given session. */
  public void setLogLevel(String sessionId, LoggingLevel level) {
    McpSession session =
        store
            .find(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    store.update(sessionId, session.withLogLevel(level));
  }

  /**
   * Encrypts plaintext into a Base64-encoded, session-bound token.
   *
   * @param sessionId the session ID used for key derivation
   * @param plaintext the value to encrypt
   * @return Base64-encoded ciphertext
   */
  public String encrypt(String sessionId, String plaintext) {
    try {
      byte[] encrypted =
          Ciphers.encryptAesGcm(masterKey, sessionId, plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /**
   * Decrypts a Base64-encoded, session-bound token back to plaintext.
   *
   * @param sessionId the session ID used for key derivation
   * @param ciphertext the Base64-encoded ciphertext
   * @return the original plaintext
   * @throws IllegalArgumentException if the token is tampered, from a different session, or
   *     malformed
   */
  public String decrypt(String sessionId, String ciphertext) {
    byte[] combined;
    try {
      combined = Base64.getDecoder().decode(ciphertext);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Malformed Base64 in encrypted token", e);
    }
    try {
      byte[] plaintext = Ciphers.decryptAesGcm(masterKey, sessionId, combined);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Invalid or tampered encrypted token", e);
    }
  }
}
