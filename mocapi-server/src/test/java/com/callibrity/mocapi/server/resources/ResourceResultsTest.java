/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.resources.ResourceContent;
import com.callibrity.mocapi.model.BlobResourceContents;
import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextResourceContents;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceResultsTest {

  private static final String URI = "ui://widget";

  @Test
  void read_resource_result_passes_through_unchanged() {
    var original =
        new ReadResourceResult(
            java.util.List.of(new TextResourceContents(URI, "text/plain", "x")),
            123L,
            CacheScope.PUBLIC,
            ResultTypes.COMPLETE);

    assertThat(ResourceResults.toResult(original, URI, "text/plain", ResourceContent.AUTO))
        .isSameAs(original);
  }

  @Test
  void null_value_throws() {
    assertThatThrownBy(() -> ResourceResults.toResult(null, URI, null, ResourceContent.AUTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(URI);
  }

  @Test
  void unsupported_value_throws() {
    assertThatThrownBy(() -> ResourceResults.toResult(42, URI, null, ResourceContent.AUTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported");
  }

  @Test
  void char_sequence_becomes_text() {
    var content =
        (TextResourceContents)
            ResourceResults.toResult(
                    new StringBuilder("hi"), URI, "text/plain", ResourceContent.AUTO)
                .contents()
                .getFirst();
    assertThat(content.text()).isEqualTo("hi");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/plain",
        "text/html",
        "application/json",
        "application/xml",
        "application/javascript",
        "text/ecmascript",
        "application/vnd.api+json",
        "image/svg+xml"
      })
  void auto_treats_text_family_mimes_as_text(String mimeType) {
    var resource = new ByteArrayResource("payload".getBytes(StandardCharsets.UTF_8));

    assertThat(
            ResourceResults.toResult(resource, URI, mimeType, ResourceContent.AUTO)
                .contents()
                .getFirst())
        .isInstanceOf(TextResourceContents.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/octet-stream", "image/png", "application/pdf"})
  void auto_treats_binary_mimes_as_blob(String mimeType) {
    var resource = new ByteArrayResource(new byte[] {1, 2, 3});

    assertThat(
            ResourceResults.toResult(resource, URI, mimeType, ResourceContent.AUTO)
                .contents()
                .getFirst())
        .isInstanceOf(BlobResourceContents.class);
  }

  @Test
  void auto_treats_blank_or_malformed_mime_as_blob() {
    var resource = new ByteArrayResource(new byte[] {1});

    assertThat(
            ResourceResults.toResult(resource, URI, "  ", ResourceContent.AUTO)
                .contents()
                .getFirst())
        .isInstanceOf(BlobResourceContents.class);
    assertThat(
            ResourceResults.toResult(resource, URI, "not a mime", ResourceContent.AUTO)
                .contents()
                .getFirst())
        .isInstanceOf(BlobResourceContents.class);
  }

  @Test
  void forced_modes_override_mime_classification() {
    var resource = new ByteArrayResource("payload".getBytes(StandardCharsets.UTF_8));

    assertThat(
            ResourceResults.toResult(
                    resource, URI, "application/octet-stream", ResourceContent.TEXT)
                .contents()
                .getFirst())
        .isInstanceOf(TextResourceContents.class);
    assertThat(
            ResourceResults.toResult(resource, URI, "text/plain", ResourceContent.BLOB)
                .contents()
                .getFirst())
        .isInstanceOf(BlobResourceContents.class);
  }

  @Test
  void text_decoding_honors_mime_charset() {
    byte[] latin1 = "café".getBytes(java.nio.charset.Charset.forName("ISO-8859-1"));
    var resource = new ByteArrayResource(latin1);

    var content =
        (TextResourceContents)
            ResourceResults.toResult(
                    resource, URI, "text/plain;charset=ISO-8859-1", ResourceContent.AUTO)
                .contents()
                .getFirst();

    assertThat(content.text()).isEqualTo("café");
  }
}
