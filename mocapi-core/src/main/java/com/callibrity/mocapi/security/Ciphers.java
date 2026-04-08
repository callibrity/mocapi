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
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stateless AES-256-GCM encryption utility. Derives a per-context key from a master key using
 * HMAC-SHA256, then encrypts/decrypts with a random nonce. Pure crypto — no domain knowledge.
 */
public final class Ciphers {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String AES_ALGORITHM = "AES";
  private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_NONCE_LENGTH = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int AES_256_KEY_LENGTH = 32;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private Ciphers() {}

  /**
   * Validates that the given key is suitable for AES-256-GCM encryption.
   *
   * @param key the master key bytes
   * @throws IllegalArgumentException if the key is null or not exactly 32 bytes
   */
  public static void validateAesGcmKey(byte[] key) {
    if (key == null || key.length != AES_256_KEY_LENGTH) {
      throw new IllegalArgumentException("Master key must be exactly 32 bytes (256 bits)");
    }
  }

  /**
   * Encrypts plaintext using AES-256-GCM with a key derived from the master key and context string.
   * Returns {@code nonce + ciphertext + auth tag} as raw bytes.
   *
   * @param masterKey the 32-byte master key
   * @param keyContext context string for key derivation (e.g. session ID)
   * @param plaintext the data to encrypt
   * @return nonce + ciphertext + GCM auth tag
   * @throws GeneralSecurityException if encryption fails
   */
  public static byte[] encryptAesGcm(byte[] masterKey, String keyContext, byte[] plaintext)
      throws GeneralSecurityException {
    SecretKey aesKey = deriveKey(masterKey, keyContext);

    byte[] nonce = new byte[GCM_NONCE_LENGTH];
    SECURE_RANDOM.nextBytes(nonce);

    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
    byte[] ciphertext = cipher.doFinal(plaintext);

    byte[] combined = new byte[nonce.length + ciphertext.length];
    System.arraycopy(nonce, 0, combined, 0, nonce.length);
    System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
    return combined;
  }

  /**
   * Decrypts ciphertext produced by {@link #encryptAesGcm}. Expects {@code nonce + ciphertext +
   * auth tag} as input.
   *
   * @param masterKey the 32-byte master key
   * @param keyContext context string for key derivation (must match encryption)
   * @param ciphertext the nonce + ciphertext + auth tag bytes
   * @return the original plaintext bytes
   * @throws GeneralSecurityException if decryption fails (tampered data, wrong key context)
   */
  public static byte[] decryptAesGcm(byte[] masterKey, String keyContext, byte[] ciphertext)
      throws GeneralSecurityException {
    if (ciphertext.length <= GCM_NONCE_LENGTH) {
      throw new GeneralSecurityException("Ciphertext too short");
    }

    byte[] nonce = Arrays.copyOfRange(ciphertext, 0, GCM_NONCE_LENGTH);
    byte[] encrypted = Arrays.copyOfRange(ciphertext, GCM_NONCE_LENGTH, ciphertext.length);

    SecretKey aesKey = deriveKey(masterKey, keyContext);
    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
    return cipher.doFinal(encrypted);
  }

  private static SecretKey deriveKey(byte[] masterKey, String keyContext)
      throws GeneralSecurityException {
    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
    mac.init(new SecretKeySpec(masterKey, HMAC_ALGORITHM));
    byte[] derived = mac.doFinal(keyContext.getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(derived, AES_ALGORITHM);
  }
}
