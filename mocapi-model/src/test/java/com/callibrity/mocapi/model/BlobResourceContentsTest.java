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
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BlobResourceContentsTest {

  @Test
  void ofEncodesBytesToBase64() {
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

    var blob = BlobResourceContents.of("img://x", "application/octet-stream", payload);

    assertThat(blob.uri()).isEqualTo("img://x");
    assertThat(blob.mimeType()).isEqualTo("application/octet-stream");
    assertThat(blob.blob()).isEqualTo("aGVsbG8=");
    assertThat(Base64.getDecoder().decode(blob.blob())).isEqualTo(payload);
  }

  @Test
  void ofHandlesEmptyArray() {
    var blob = BlobResourceContents.of("img://x", "image/png", new byte[0]);

    assertThat(blob.blob()).isEmpty();
  }

  @Test
  void ofRoundTripsBinaryContent() {
    byte[] payload = {(byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0xC3, (byte) 0xA9};

    var blob = BlobResourceContents.of("img://x", "image/png", payload);

    assertThat(Base64.getDecoder().decode(blob.blob())).isEqualTo(payload);
  }

  @Test
  void ofAllowsNullMimeType() {
    var blob = BlobResourceContents.of("img://x", null, new byte[] {1, 2, 3});

    assertThat(blob.mimeType()).isNull();
    assertThat(blob.blob()).isEqualTo(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
  }

  @Test
  void canonicalConstructorStillAcceptsPreEncodedString() {
    String alreadyEncoded =
        Base64.getEncoder().encodeToString("hi".getBytes(StandardCharsets.UTF_8));

    var blob = new BlobResourceContents("img://x", "image/png", alreadyEncoded);

    assertThat(blob.blob()).isEqualTo(alreadyEncoded);
  }
}
