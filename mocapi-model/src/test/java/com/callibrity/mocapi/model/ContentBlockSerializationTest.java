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
class ContentBlockSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void text_content_round_trip() throws Exception {
    var original = new TextContent("hello", null);
    String json = mapper.writeValueAsString(original);
    assertThat(json)
        .contains("\"type\":\"text\"")
        .contains("\"text\":\"hello\"")
        .doesNotContain("annotations");

    ContentBlock deserialized = mapper.readValue(json, ContentBlock.class);
    assertThat(deserialized).isInstanceOf(TextContent.class);
    assertThat(((TextContent) deserialized).text()).isEqualTo("hello");
  }

  @Test
  void image_content_round_trip() throws Exception {
    var original = new ImageContent("base64data", "image/png", null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"image\"");

    ContentBlock deserialized = mapper.readValue(json, ContentBlock.class);
    assertThat(deserialized).isInstanceOf(ImageContent.class);
    var img = (ImageContent) deserialized;
    assertThat(img.data()).isEqualTo("base64data");
    assertThat(img.mimeType()).isEqualTo("image/png");
  }

  @Test
  void audio_content_round_trip() throws Exception {
    var original = new AudioContent("audiodata", "audio/wav", null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"audio\"");

    ContentBlock deserialized = mapper.readValue(json, ContentBlock.class);
    assertThat(deserialized).isInstanceOf(AudioContent.class);
  }

  @Test
  void resource_link_round_trip() throws Exception {
    var original = new ResourceLink("file:///test.txt", "text/plain", null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"resource_link\"");

    ContentBlock deserialized = mapper.readValue(json, ContentBlock.class);
    assertThat(deserialized).isInstanceOf(ResourceLink.class);
    assertThat(((ResourceLink) deserialized).uri()).isEqualTo("file:///test.txt");
  }

  @Test
  void embedded_resource_round_trip() throws Exception {
    var resource = new TextResourceContents("file:///test.txt", "text/plain", "content");
    var original = new EmbeddedResource(resource, null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"resource\"");

    ContentBlock deserialized = mapper.readValue(json, ContentBlock.class);
    assertThat(deserialized).isInstanceOf(EmbeddedResource.class);
    var embedded = (EmbeddedResource) deserialized;
    assertThat(embedded.resource()).isInstanceOf(TextResourceContents.class);
    assertThat(((TextResourceContents) embedded.resource()).text()).isEqualTo("content");
  }

  @Test
  void text_content_with_annotations() throws Exception {
    var annotations = new Annotations(List.of(Role.USER), 0.8, "2025-01-01T00:00:00Z");
    var original = new TextContent("annotated", annotations);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"audience\":[\"user\"]").contains("\"priority\":0.8");

    ContentBlock deserialized = mapper.readValue(json, ContentBlock.class);
    assertThat(deserialized).isInstanceOf(TextContent.class);
    var text = (TextContent) deserialized;
    assertThat(text.annotations().audience()).containsExactly(Role.USER);
    assertThat(text.annotations().priority()).isEqualTo(0.8);
  }
}
