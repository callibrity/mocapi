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
package com.callibrity.mocapi.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Produces opaque, tamper-proof, session-bound IDs using AES-256-GCM. Used for SSE event IDs and
 * JSON-RPC request IDs (elicitation, sampling). Clients store and echo these IDs but cannot read,
 * forge, or reuse them across sessions.
 *
 * <p>Thread-safe. Construct once with the master key bytes and reuse across requests.
 */
public class McpEventIdCodec {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String AES_ALGORITHM = "AES";
  private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_NONCE_LENGTH = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private final byte[] masterKeyBytes;
  private final SecureRandom secureRandom = new SecureRandom();

  public McpEventIdCodec(byte[] masterKeyBytes) {
    if (masterKeyBytes == null || masterKeyBytes.length != 32) {
      throw new IllegalArgumentException("Master key must be exactly 32 bytes (256 bits)");
    }
    this.masterKeyBytes = Arrays.copyOf(masterKeyBytes, masterKeyBytes.length);
  }

  /**
   * Encrypts plaintext into a Base64-encoded, session-bound token.
   *
   * @param sessionId the session ID used for key derivation
   * @param plaintext the value to encrypt
   * @return Base64-encoded ciphertext safe for HTTP headers
   */
  public String encode(String sessionId, String plaintext) {
    try {
      SecretKey aesKey = deriveKey(sessionId);

      byte[] nonce = new byte[GCM_NONCE_LENGTH];
      secureRandom.nextBytes(nonce);

      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[nonce.length + ciphertext.length];
      System.arraycopy(nonce, 0, combined, 0, nonce.length);
      System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /**
   * Decrypts a Base64-encoded token back to plaintext.
   *
   * @param sessionId the session ID used for key derivation (must match the one used for encoding)
   * @param encoded the Base64-encoded ciphertext
   * @return the original plaintext
   * @throws McpEventIdCodecException if the token is tampered, from a different session, or
   *     malformed
   */
  public String decode(String sessionId, String encoded) {
    byte[] combined;
    try {
      combined = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new McpEventIdCodecException("Malformed Base64 in event ID", e);
    }

    if (combined.length <= GCM_NONCE_LENGTH) {
      throw new McpEventIdCodecException("Event ID too short");
    }

    byte[] nonce = Arrays.copyOfRange(combined, 0, GCM_NONCE_LENGTH);
    byte[] ciphertext = Arrays.copyOfRange(combined, GCM_NONCE_LENGTH, combined.length);

    try {
      SecretKey aesKey = deriveKey(sessionId);
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new McpEventIdCodecException("Invalid or tampered event ID", e);
    }
  }

  private SecretKey deriveKey(String sessionId) throws GeneralSecurityException {
    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
    mac.init(new SecretKeySpec(masterKeyBytes, HMAC_ALGORITHM));
    byte[] derived = mac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(derived, AES_ALGORITHM);
  }
}
