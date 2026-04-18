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

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceContentsSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void text_resource_contents_round_trip() throws Exception {
    var original = new TextResourceContents("file:///test.txt", "text/plain", "hello");
    String json = mapper.writeValueAsString(original);

    ResourceContents deserialized = mapper.readValue(json, ResourceContents.class);
    assertThat(deserialized).isInstanceOf(TextResourceContents.class);
    var text = (TextResourceContents) deserialized;
    assertThat(text.uri()).isEqualTo("file:///test.txt");
    assertThat(text.text()).isEqualTo("hello");
  }

  @Test
  void blob_resource_contents_round_trip() throws Exception {
    var original = new BlobResourceContents("file:///test.bin", "application/octet-stream", "YmFz");
    String json = mapper.writeValueAsString(original);

    ResourceContents deserialized = mapper.readValue(json, ResourceContents.class);
    assertThat(deserialized).isInstanceOf(BlobResourceContents.class);
    var blob = (BlobResourceContents) deserialized;
    assertThat(blob.uri()).isEqualTo("file:///test.bin");
    assertThat(blob.blob()).isEqualTo("YmFz");
  }

  @Test
  void read_resource_result_round_trip() throws Exception {
    var text = new TextResourceContents("file:///a.txt", "text/plain", "hello");
    var blob = new BlobResourceContents("file:///b.bin", "application/octet-stream", "YmFz");
    var original = new ReadResourceResult(List.of(text, blob));
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"contents\":[");

    var deserialized = mapper.readValue(json, ReadResourceResult.class);
    assertThat(deserialized.contents())
        .satisfies(
            contents -> {
              assertThat(contents).hasSize(2);
              assertThat(contents.get(0)).isInstanceOf(TextResourceContents.class);
              assertThat(contents.get(1)).isInstanceOf(BlobResourceContents.class);
            });
  }
}
