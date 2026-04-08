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

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpEventIdCodecTest {

  private byte[] masterKey;
  private McpEventIdCodec codec;

  @BeforeEach
  void setUp() {
    masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    codec = new McpEventIdCodec(masterKey);
  }

  @Test
  void encodeProducesBase64Output() {
    String encoded = codec.encode("session-1", "streamKey/entry123");
    assertThat(encoded).matches("[A-Za-z0-9+/=]+");
  }

  @Test
  void decodeReturnsOriginalPlaintext() {
    String plaintext = "streamKey:abc/entry-42";
    String encoded = codec.encode("session-1", plaintext);
    String decoded = codec.decode("session-1", encoded);
    assertThat(decoded).isEqualTo(plaintext);
  }

  @Test
  void decodingWithWrongSessionIdThrows() {
    String encoded = codec.encode("session-1", "some-data");
    assertThatThrownBy(() -> codec.decode("session-2", encoded))
        .isInstanceOf(McpEventIdCodecException.class)
        .hasMessageContaining("Invalid or tampered");
  }

  @Test
  void decodingTamperedCiphertextThrows() {
    String encoded = codec.encode("session-1", "some-data");
    byte[] bytes = Base64.getDecoder().decode(encoded);
    bytes[bytes.length - 1] ^= 0xFF;
    String tampered = Base64.getEncoder().encodeToString(bytes);
    assertThatThrownBy(() -> codec.decode("session-1", tampered))
        .isInstanceOf(McpEventIdCodecException.class)
        .hasMessageContaining("Invalid or tampered");
  }

  @Test
  void differentSessionsProduceDifferentCiphertexts() {
    String plaintext = "same-plaintext";
    String encoded1 = codec.encode("session-A", plaintext);
    String encoded2 = codec.encode("session-B", plaintext);
    assertThat(encoded1).isNotEqualTo(encoded2);
  }

  @Test
  void sameSessionAndPlaintextProducesDifferentCiphertexts() {
    String plaintext = "same-plaintext";
    String encoded1 = codec.encode("session-1", plaintext);
    String encoded2 = codec.encode("session-1", plaintext);
    assertThat(encoded1).isNotEqualTo(encoded2);
  }

  @Test
  void bothEncodingsDecodeToSamePlaintext() {
    String plaintext = "stream:main/entry-99";
    String encoded1 = codec.encode("session-1", plaintext);
    String encoded2 = codec.encode("session-1", plaintext);
    assertThat(codec.decode("session-1", encoded1)).isEqualTo(plaintext);
    assertThat(codec.decode("session-1", encoded2)).isEqualTo(plaintext);
  }

  @Test
  void malformedBase64Throws() {
    assertThatThrownBy(() -> codec.decode("session-1", "not!valid!base64!!!"))
        .isInstanceOf(McpEventIdCodecException.class)
        .hasMessageContaining("Malformed Base64");
  }

  @Test
  void tooShortInputThrows() {
    String tooShort = Base64.getEncoder().encodeToString(new byte[5]);
    assertThatThrownBy(() -> codec.decode("session-1", tooShort))
        .isInstanceOf(McpEventIdCodecException.class)
        .hasMessageContaining("too short");
  }

  @Test
  void constructorRejectsNullKey() {
    assertThatThrownBy(() -> new McpEventIdCodec(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void constructorRejectsWrongKeyLength() {
    assertThatThrownBy(() -> new McpEventIdCodec(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void roundTripWithElicitationMailboxKey() {
    String mailboxKey = "elicit:550e8400-e29b-41d4-a716-446655440000";
    String sessionId = "sess-abc-123";
    String encoded = codec.encode(sessionId, mailboxKey);
    String decoded = codec.decode(sessionId, encoded);
    assertThat(decoded).isEqualTo(mailboxKey);
  }

  @Test
  void roundTripWithSseEventIdFormat() {
    String sseId = "notifications:sess-abc-123/journal-entry-42";
    String sessionId = "sess-abc-123";
    String encoded = codec.encode(sessionId, sseId);
    String decoded = codec.decode(sessionId, encoded);
    assertThat(decoded).isEqualTo(sseId);
    String[] parts = decoded.split("/", 2);
    assertThat(parts[0]).isEqualTo("notifications:sess-abc-123");
    assertThat(parts[1]).isEqualTo("journal-entry-42");
  }
}
