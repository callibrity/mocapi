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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompletionRefSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void prompt_reference_round_trip() throws Exception {
    var original = new PromptReference("ref/prompt", "greeting");
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"ref/prompt\"").contains("\"name\":\"greeting\"");

    var deserialized = mapper.readValue(json, CompletionRef.class);
    assertThat(deserialized).isInstanceOf(PromptReference.class);
    var ref = (PromptReference) deserialized;
    assertThat(ref.name()).isEqualTo("greeting");
  }

  @Test
  void resource_template_reference_round_trip() throws Exception {
    var original = new ResourceTemplateReference("ref/resource", "file:///{path}");
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"ref/resource\"").contains("\"uri\":\"file:///{path}\"");

    var deserialized = mapper.readValue(json, CompletionRef.class);
    assertThat(deserialized).isInstanceOf(ResourceTemplateReference.class);
    var ref = (ResourceTemplateReference) deserialized;
    assertThat(ref.uri()).isEqualTo("file:///{path}");
  }
}
