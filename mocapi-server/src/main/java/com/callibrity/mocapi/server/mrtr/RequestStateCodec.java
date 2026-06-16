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
package com.callibrity.mocapi.server.mrtr;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Encodes and decodes the opaque MRTR {@code requestState} token (ADR-0021). The token is the
 * JSON-serialized {@link RequestStatePayload} encrypted with AES-256-GCM (a fresh random 96-bit
 * nonce per encode, prepended to the ciphertext) and Base64URL-encoded. GCM is an AEAD mode, so the
 * blob is simultaneously confidential (clients cannot read the ledger) and tamper-evident (any
 * modification — or a decode attempt with a different key — fails authentication). The server
 * stores nothing; the token is self-contained.
 *
 * <p><strong>Key configuration.</strong> Production deployments set {@code mocapi.mrtr.secret} to a
 * Base64-encoded 256-bit key shared by every instance ({@link #withSecret}). When the property is
 * unset, {@link #withEphemeralKey} generates a random key at startup and logs a prominent warning:
 * in-flight elicitations then cannot survive a restart and cannot be retried against another
 * instance.
 *
 * <p><strong>TTL.</strong> Tokens older than the configured TTL ({@code mocapi.mrtr.ttl}, default
 * {@link #DEFAULT_TTL} — aligned with the retired {@code mocapi.elicitation.timeout} default) are
 * rejected with {@link ExpiredRequestStateException}.
 */
public final class RequestStateCodec {

  /** Default token TTL, aligned with the retired {@code mocapi.elicitation.timeout} (PT5M). */
  public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private static final Logger log = LoggerFactory.getLogger(RequestStateCodec.class);

  private static final String AES = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int KEY_LENGTH = 32;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKey key;
  private final Duration ttl;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  private RequestStateCodec(SecretKey key, Duration ttl, ObjectMapper objectMapper, Clock clock) {
    this.key = key;
    this.ttl = Objects.requireNonNull(ttl, "ttl");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Creates a codec from a configured secret ({@code mocapi.mrtr.secret}).
   *
   * @param base64Secret Base64-encoded 256-bit key
   * @param ttl token time-to-live ({@code mocapi.mrtr.ttl})
   * @param objectMapper mapper used to (de)serialize the payload
   * @throws IllegalArgumentException if the secret is not valid Base64 or not exactly 256 bits
   */
  public static RequestStateCodec withSecret(
      String base64Secret, Duration ttl, ObjectMapper objectMapper) {
    return withSecret(base64Secret, ttl, objectMapper, Clock.systemUTC());
  }

  /** {@link #withSecret(String, Duration, ObjectMapper)} with an explicit clock (for tests). */
  public static RequestStateCodec withSecret(
      String base64Secret, Duration ttl, ObjectMapper objectMapper, Clock clock) {
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(base64Secret);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("mocapi.mrtr.secret must be valid Base64", e);
    }
    if (keyBytes.length != KEY_LENGTH) {
      throw new IllegalArgumentException(
          String.format(
              "mocapi.mrtr.secret must decode to exactly %d bytes (256 bits), got %d",
              KEY_LENGTH, keyBytes.length));
    }
    return new RequestStateCodec(new SecretKeySpec(keyBytes, AES), ttl, objectMapper, clock);
  }

  /**
   * Creates a codec with a freshly generated random key, for deployments that have not configured
   * {@code mocapi.mrtr.secret}. Logs a prominent warning: tokens minted with an ephemeral key are
   * worthless after a restart and unreadable by other instances.
   */
  public static RequestStateCodec withEphemeralKey(Duration ttl, ObjectMapper objectMapper) {
    return withEphemeralKey(ttl, objectMapper, Clock.systemUTC());
  }

  /** {@link #withEphemeralKey(Duration, ObjectMapper)} with an explicit clock (for tests). */
  public static RequestStateCodec withEphemeralKey(
      Duration ttl, ObjectMapper objectMapper, Clock clock) {
    log.warn(
        "No mocapi.mrtr.secret configured — generated an ephemeral MRTR requestState key. "
            + "In-flight elicitations will NOT survive restarts and will NOT work across "
            + "multiple instances; set mocapi.mrtr.secret (Base64-encoded 256-bit key) for "
            + "production.");
    byte[] keyBytes = new byte[KEY_LENGTH];
    SECURE_RANDOM.nextBytes(keyBytes);
    return new RequestStateCodec(new SecretKeySpec(keyBytes, AES), ttl, objectMapper, clock);
  }

  /**
   * Mints a {@code requestState} token for the given conversation state, stamped with the current
   * time.
   *
   * @param method the JSON-RPC method the original request arrived on
   * @param originalParams the original params (already stripped of {@code _meta}, {@code
   *     inputResponses}, {@code requestState})
   * @param inputResponses the response ledger in call-ordinal order
   * @return the opaque Base64URL token, bound to no principal
   */
  public String encode(
      String method, JsonNode originalParams, List<ResponseLedgerEntry> inputResponses) {
    return encode(method, originalParams, inputResponses, null);
  }

  /**
   * Mints a {@code requestState} token bound to the given authenticated principal, stamped with the
   * current time. A retry presented by a different principal is rejected at decode time.
   *
   * @param method the JSON-RPC method the original request arrived on
   * @param originalParams the original params (already stripped of {@code _meta}, {@code
   *     inputResponses}, {@code requestState})
   * @param inputResponses the response ledger in call-ordinal order
   * @param principal the authenticated principal, or {@code null} when unauthenticated
   * @return the opaque Base64URL token
   */
  public String encode(
      String method,
      JsonNode originalParams,
      List<ResponseLedgerEntry> inputResponses,
      String principal) {
    var payload =
        new RequestStatePayload(
            method, originalParams, List.copyOf(inputResponses), clock.millis(), principal);
    byte[] plaintext = objectMapper.writeValueAsBytes(payload);
    byte[] nonce = new byte[NONCE_LENGTH];
    SECURE_RANDOM.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(plaintext);
      byte[] combined = new byte[nonce.length + ciphertext.length];
      System.arraycopy(nonce, 0, combined, 0, nonce.length);
      System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      // AES/GCM/NoPadding is mandated by the Java platform; reaching this indicates a broken JVM.
      throw new IllegalStateException("AES-GCM encryption unavailable", e);
    }
  }

  /**
   * Verifies, decrypts, and parses a {@code requestState} token.
   *
   * @param requestState the opaque token from the client's retry
   * @return the decoded payload
   * @throws InvalidRequestStateException if the token is malformed, fails authentication (tampered
   *     or minted under a different key), or carries an unparseable payload
   * @throws ExpiredRequestStateException if the token is authentic but older than the TTL
   */
  public RequestStatePayload decode(String requestState) {
    byte[] combined;
    try {
      combined = Base64.getUrlDecoder().decode(requestState);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestStateException("requestState is not valid Base64URL", e);
    }
    if (combined.length <= NONCE_LENGTH) {
      throw new InvalidRequestStateException("requestState is too short to be a valid token");
    }
    byte[] nonce = Arrays.copyOfRange(combined, 0, NONCE_LENGTH);
    byte[] ciphertext = Arrays.copyOfRange(combined, NONCE_LENGTH, combined.length);
    byte[] plaintext;
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      plaintext = cipher.doFinal(ciphertext);
    } catch (GeneralSecurityException e) {
      throw new InvalidRequestStateException(
          "requestState failed authentication (tampered, or issued under a different key)", e);
    }
    RequestStatePayload payload;
    try {
      payload = objectMapper.readValue(plaintext, RequestStatePayload.class);
    } catch (JacksonException e) {
      throw new InvalidRequestStateException("requestState payload is malformed", e);
    }
    if (payload.issuedAt() + ttl.toMillis() < clock.millis()) {
      throw new ExpiredRequestStateException(
          "requestState has expired; restart the original request");
    }
    return payload;
  }
}
