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

// SEP-2577 spec contract: CreateMessageResult is deprecated but remains in the specification for
// the deprecation window; these tests exercise the retained 1:1 model type.
@SuppressWarnings("deprecation")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CreateMessageResultTest {

  @Test
  void text_returns_string_when_content_contains_a_text_content_block() {
    var result =
        new CreateMessageResult(
            Role.ASSISTANT, List.of(new TextContent("Hello", null)), "gpt", null);
    assertThat(result.text()).isEqualTo("Hello");
  }

  @Test
  void text_returns_null_when_content_has_no_text_content_block() {
    var result =
        new CreateMessageResult(
            Role.ASSISTANT, List.of(new ImageContent("data", "image/png", null)), "gpt", null);
    assertThat(result.text()).isNull();
  }

  @Test
  void text_returns_null_when_content_is_null() {
    var result = new CreateMessageResult(Role.ASSISTANT, null, "gpt", null);
    assertThat(result.text()).isNull();
  }
}
