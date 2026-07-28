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

// SEP-2577 spec contract: ToolChoice is deprecated along with the sampling feature but remains in
// the specification for the deprecation window; these tests exercise the retained 1:1 model.
@SuppressWarnings("deprecation")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolChoiceTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void serializes_as_a_mode_object() throws Exception {
    String json = mapper.writeValueAsString(new ToolChoice(ToolChoice.MODE_AUTO));
    assertThat(json).isEqualTo("{\"mode\":\"auto\"}");
  }

  @Test
  void omits_null_mode() throws Exception {
    String json = mapper.writeValueAsString(new ToolChoice(null));
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void deserializes_each_spec_mode_value() throws Exception {
    assertThat(mapper.readValue("{\"mode\":\"auto\"}", ToolChoice.class).mode())
        .isEqualTo(ToolChoice.MODE_AUTO);
    assertThat(mapper.readValue("{\"mode\":\"required\"}", ToolChoice.class).mode())
        .isEqualTo(ToolChoice.MODE_REQUIRED);
    assertThat(mapper.readValue("{\"mode\":\"none\"}", ToolChoice.class).mode())
        .isEqualTo(ToolChoice.MODE_NONE);
  }
}
