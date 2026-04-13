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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class CiphersTest {

  private static final SecureRandom RANDOM = new SecureRandom();

  private static byte[] validKey() {
    byte[] key = new byte[32];
    RANDOM.nextBytes(key);
    return key;
  }

  @Test
  void validateAesGcmKeyShouldRejectNullKey() {
    assertThatThrownBy(() -> Ciphers.validateAesGcmKey(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void validateAesGcmKeyShouldRejectShortKey() {
    assertThatThrownBy(() -> Ciphers.validateAesGcmKey(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void validateAesGcmKeyShouldAcceptValidKey() {
    Ciphers.validateAesGcmKey(validKey());
  }

  @Test
  void decryptShouldRejectCiphertextTooShort() {
    byte[] key = validKey();
    // 12 bytes or fewer is too short (nonce is 12 bytes, need at least 1 byte of ciphertext)
    assertThatThrownBy(() -> Ciphers.decryptAesGcm(key, "ctx", new byte[12]))
        .isInstanceOf(GeneralSecurityException.class)
        .hasMessageContaining("too short");
  }

  @Test
  void roundTripEncryptDecryptShouldProduceOriginalPlaintext() throws GeneralSecurityException {
    byte[] key = validKey();
    byte[] plaintext = "hello, world".getBytes(StandardCharsets.UTF_8);

    byte[] ciphertext = Ciphers.encryptAesGcm(key, "test-context", plaintext);
    byte[] decrypted = Ciphers.decryptAesGcm(key, "test-context", ciphertext);

    assertThat(decrypted).isEqualTo(plaintext);
  }
}
