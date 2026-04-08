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

import com.callibrity.mocapi.security.Ciphers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Single public API for all session concerns — lifecycle, encryption, and lookup. The controller
 * and stream context interact with sessions through this service only.
 */
public class McpSessionService {

  private final McpSessionStore store;
  private final byte[] masterKey;
  private final Duration ttl;

  public McpSessionService(McpSessionStore store, byte[] masterKey, Duration ttl) {
    Ciphers.validateAesGcmKey(masterKey);
    this.store = store;
    this.masterKey = masterKey.clone();
    this.ttl = ttl;
  }

  /** Saves the session to the store and returns the generated session ID. */
  public String create(McpSession session) {
    return store.save(session, ttl);
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

  /** Removes a session from the store. */
  public void delete(String sessionId) {
    store.delete(sessionId);
  }

  /** Updates the log level for the given session. */
  public void setLogLevel(String sessionId, LogLevel level) {
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
