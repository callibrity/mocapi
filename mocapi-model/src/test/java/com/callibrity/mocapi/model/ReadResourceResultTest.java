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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReadResourceResultTest {

  @Nested
  class Of_text {

    @Test
    void wraps_single_text_resource_contents() {
      var result = ReadResourceResult.ofText("docs://readme", "text/markdown", "# Hello");

      assertThat(result.contents()).hasSize(1);
      var only = result.contents().getFirst();
      assertThat(only).isInstanceOf(TextResourceContents.class);
      var text = (TextResourceContents) only;
      assertThat(text.uri()).isEqualTo("docs://readme");
      assertThat(text.mimeType()).isEqualTo("text/markdown");
      assertThat(text.text()).isEqualTo("# Hello");
    }

    @Test
    void allows_null_mime_type() {
      var result = ReadResourceResult.ofText("docs://x", null, "body");

      var text = (TextResourceContents) result.contents().getFirst();
      assertThat(text.mimeType()).isNull();
      assertThat(text.text()).isEqualTo("body");
    }

    @Test
    void allows_empty_text() {
      var result = ReadResourceResult.ofText("docs://x", "text/plain", "");

      var text = (TextResourceContents) result.contents().getFirst();
      assertThat(text.text()).isEmpty();
    }

    @Test
    void result_contents_list_is_immutable() {
      var result = ReadResourceResult.ofText("docs://x", "text/plain", "body");

      assertThat(result.contents()).isUnmodifiable();
    }
  }

  @Nested
  class Of_blob {

    @Test
    void string_variant_passes_blob_through_unchanged() {
      String alreadyEncoded =
          Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
      var result = ReadResourceResult.ofBlob("img://red.png", "image/png", alreadyEncoded);

      var blob = (BlobResourceContents) result.contents().getFirst();
      assertThat(blob.uri()).isEqualTo("img://red.png");
      assertThat(blob.mimeType()).isEqualTo("image/png");
      assertThat(blob.blob()).isEqualTo(alreadyEncoded);
    }

    @Test
    void bytes_variant_encodes_to_base64() {
      byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
      var result = ReadResourceResult.ofBlob("img://x", "application/octet-stream", payload);

      var blob = (BlobResourceContents) result.contents().getFirst();
      assertThat(blob.blob()).isEqualTo("aGVsbG8=");
      assertThat(Base64.getDecoder().decode(blob.blob())).isEqualTo(payload);
    }

    @Test
    void bytes_variant_handles_empty_array() {
      var result = ReadResourceResult.ofBlob("img://x", "image/png", new byte[0]);

      var blob = (BlobResourceContents) result.contents().getFirst();
      assertThat(blob.blob()).isEmpty();
    }

    @Test
    void bytes_variant_round_trips_binary_content() {
      byte[] payload = {(byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0xC3, (byte) 0xA9};
      var result = ReadResourceResult.ofBlob("img://x", "image/png", payload);

      var blob = (BlobResourceContents) result.contents().getFirst();
      assertThat(Base64.getDecoder().decode(blob.blob())).isEqualTo(payload);
    }

    @Test
    void string_variant_allows_null_mime_type() {
      var result = ReadResourceResult.ofBlob("img://x", null, "aGk=");

      var blob = (BlobResourceContents) result.contents().getFirst();
      assertThat(blob.mimeType()).isNull();
    }

    @Test
    void bytes_variant_result_contents_list_is_immutable() {
      var result = ReadResourceResult.ofBlob("img://x", "image/png", new byte[] {1, 2, 3});

      assertThat(result.contents()).isUnmodifiable();
    }
  }

  @Test
  void regular_constructor_still_works_for_multiple_contents() {
    var result =
        new ReadResourceResult(
            java.util.List.of(
                new TextResourceContents("docs://a", "text/plain", "A"),
                new BlobResourceContents("docs://b", "image/png", "aGk=")));

    assertThat(result.contents()).hasSize(2);
    assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
    assertThat(result.contents().get(1)).isInstanceOf(BlobResourceContents.class);
  }
}
