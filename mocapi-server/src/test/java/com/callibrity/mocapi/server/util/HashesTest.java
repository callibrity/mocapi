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
package com.callibrity.mocapi.server.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HashesTest {

  private static final String HELLO_SHA256 =
      "sha256:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
  private static final String EMPTY_SHA256 =
      "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @Test
  void sha256Of_String_matches_known_vector() {
    assertThat(Hashes.sha256Of("hello")).isEqualTo(HELLO_SHA256);
  }

  @Test
  void sha256Of_empty_String_matches_known_vector() {
    assertThat(Hashes.sha256Of("")).isEqualTo(EMPTY_SHA256);
  }

  @Test
  void sha256Of_varargs_concatenates_without_delimiter() {
    assertThat(Hashes.sha256Of("a", "b")).isEqualTo(Hashes.sha256Of("ab"));
  }

  @Test
  void sha256Of_empty_varargs_hashes_empty_string() {
    assertThat(Hashes.sha256Of(new String[0])).isEqualTo(EMPTY_SHA256);
  }

  @Test
  void sha256Of_varargs_rejects_null_chunk() {
    assertThatNullPointerException()
        .isThrownBy(() -> Hashes.sha256Of("a", null, "b"))
        .withMessageContaining("chunks[1]");
  }

  @Test
  void sha256Of_varargs_rejects_null_array() {
    assertThatNullPointerException().isThrownBy(() -> Hashes.sha256Of((String[]) null));
  }

  @Test
  void sha256Of_String_rejects_null() {
    assertThatNullPointerException().isThrownBy(() -> Hashes.sha256Of((String) null));
  }

  @Test
  void sha256Of_bytes_rejects_null() {
    assertThatNullPointerException().isThrownBy(() -> Hashes.sha256Of((byte[]) null));
  }

  @Test
  void sha256Of_bytes_matches_sha256Of_String_for_same_utf8_bytes() {
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
    assertThat(Hashes.sha256Of(bytes)).isEqualTo(Hashes.sha256Of("hello"));
  }

  @Test
  void sha256Of_output_is_prefixed_hex_of_expected_length() {
    String hash = Hashes.sha256Of("anything");
    assertThat(hash).startsWith("sha256:").hasSize("sha256:".length() + 64);
    assertThat(hash.substring("sha256:".length())).matches("[0-9a-f]{64}");
  }
}
