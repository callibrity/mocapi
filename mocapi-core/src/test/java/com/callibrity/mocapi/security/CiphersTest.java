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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CiphersTest {

  private byte[] masterKey;

  @BeforeEach
  void setUp() {
    masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
  }

  @Test
  void validateAesGcmKeyAcceptsValidKey() {
    Ciphers.validateAesGcmKey(masterKey);
  }

  @Test
  void validateAesGcmKeyRejectsNull() {
    assertThatThrownBy(() -> Ciphers.validateAesGcmKey(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void validateAesGcmKeyRejectsWrongLength() {
    assertThatThrownBy(() -> Ciphers.validateAesGcmKey(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void encryptDecryptRoundTrip() throws GeneralSecurityException {
    byte[] plaintext = "hello world".getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = Ciphers.encryptAesGcm(masterKey, "session-1", plaintext);
    byte[] decrypted = Ciphers.decryptAesGcm(masterKey, "session-1", ciphertext);
    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void differentContextsProduceDifferentCiphertexts() throws GeneralSecurityException {
    byte[] plaintext = "same-plaintext".getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext1 = Ciphers.encryptAesGcm(masterKey, "session-A", plaintext);
    byte[] ciphertext2 = Ciphers.encryptAesGcm(masterKey, "session-B", plaintext);
    assertThat(ciphertext1).isNotEqualTo(ciphertext2);
  }

  @Test
  void sameContextProducesDifferentCiphertexts() throws GeneralSecurityException {
    byte[] plaintext = "same-plaintext".getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext1 = Ciphers.encryptAesGcm(masterKey, "session-1", plaintext);
    byte[] ciphertext2 = Ciphers.encryptAesGcm(masterKey, "session-1", plaintext);
    assertThat(ciphertext1).isNotEqualTo(ciphertext2);
  }

  @Test
  void decryptWithWrongContextThrows() throws GeneralSecurityException {
    byte[] plaintext = "data".getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = Ciphers.encryptAesGcm(masterKey, "session-1", plaintext);
    assertThatThrownBy(() -> Ciphers.decryptAesGcm(masterKey, "session-2", ciphertext))
        .isInstanceOf(GeneralSecurityException.class);
  }

  @Test
  void decryptTamperedCiphertextThrows() throws GeneralSecurityException {
    byte[] plaintext = "data".getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = Ciphers.encryptAesGcm(masterKey, "session-1", plaintext);
    ciphertext[ciphertext.length - 1] ^= 0xFF;
    byte[] tampered = ciphertext;
    assertThatThrownBy(() -> Ciphers.decryptAesGcm(masterKey, "session-1", tampered))
        .isInstanceOf(GeneralSecurityException.class);
  }

  @Test
  void decryptTooShortInputThrows() {
    assertThatThrownBy(() -> Ciphers.decryptAesGcm(masterKey, "session-1", new byte[5]))
        .isInstanceOf(GeneralSecurityException.class)
        .hasMessageContaining("too short");
  }
}
