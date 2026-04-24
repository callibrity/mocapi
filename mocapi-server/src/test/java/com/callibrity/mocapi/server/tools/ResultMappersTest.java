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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Unit coverage for each concrete {@link ResultMapper}. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResultMappersTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  class VoidResultMapper_tests {

    @Test
    void null_input_produces_empty_content_array_no_error_no_structured_content() {
      CallToolResult out = VoidResultMapper.INSTANCE.map(null);
      assertThat(out.isError()).isNull();
      assertThat(out.structuredContent()).isNull();
      assertThat(out.content()).isEmpty();
    }

    @Test
    void non_null_input_is_ignored_same_empty_result_is_produced() {
      CallToolResult out = VoidResultMapper.INSTANCE.map("anything");
      assertThat(out.content()).isEmpty();
    }

    @Test
    void singleton_instance_is_stable() {
      assertThat(VoidResultMapper.INSTANCE).isSameAs(VoidResultMapper.INSTANCE);
    }
  }

  @Nested
  class PassthroughResultMapper_tests {

    @Test
    void CallToolResult_input_is_returned_as_is() {
      CallToolResult in = new CallToolResult(List.of(new TextContent("hello", null)), null, null);

      CallToolResult out = PassthroughResultMapper.INSTANCE.map(in);

      assertThat(out).isSameAs(in);
    }

    @Test
    void null_input_becomes_empty_content_CallToolResult() {
      CallToolResult out = PassthroughResultMapper.INSTANCE.map(null);
      assertThat(out.isError()).isNull();
      assertThat(out.structuredContent()).isNull();
      assertThat(out.content()).isEmpty();
    }

    @Test
    void singleton_instance_is_stable() {
      assertThat(PassthroughResultMapper.INSTANCE).isSameAs(PassthroughResultMapper.INSTANCE);
    }
  }

  @Nested
  class TextContentResultMapper_tests {

    @Test
    void String_input_becomes_single_text_content_block_with_no_structured_content() {
      CallToolResult out = TextContentResultMapper.INSTANCE.map("hello");
      assertThat(out.isError()).isNull();
      assertThat(out.structuredContent()).isNull();
      assertThat(out.content()).hasSize(1);
      assertThat(((TextContent) out.content().getFirst()).text()).isEqualTo("hello");
    }

    @Test
    void StringBuilder_input_is_toStringed() {
      CallToolResult out = TextContentResultMapper.INSTANCE.map(new StringBuilder("composed"));
      assertThat(((TextContent) out.content().getFirst()).text()).isEqualTo("composed");
    }

    @Test
    void null_input_produces_empty_text_block() {
      CallToolResult out = TextContentResultMapper.INSTANCE.map(null);
      assertThat(((TextContent) out.content().getFirst()).text()).isEmpty();
    }

    @Test
    void empty_String_input_is_preserved() {
      CallToolResult out = TextContentResultMapper.INSTANCE.map("");
      assertThat(((TextContent) out.content().getFirst()).text()).isEmpty();
    }
  }

  @Nested
  class StructuredResultMapper_tests {

    private final StructuredResultMapper structured = new StructuredResultMapper(mapper);

    @Test
    void record_input_is_serialized_into_structured_content_and_text_content() {
      CallToolResult out = structured.map(new Person("Ada", 36));
      assertThat(out.isError()).isNull();
      assertThat(out.structuredContent()).isNotNull();
      assertThat(out.structuredContent().get("name").asString()).isEqualTo("Ada");
      assertThat(out.structuredContent().get("age").asInt()).isEqualTo(36);
      assertThat(out.content()).hasSize(1);
      // The text content mirrors structuredContent.toString() — a canonical JSON rendering.
      String text = ((TextContent) out.content().getFirst()).text();
      assertThat(text).contains("\"name\":\"Ada\"").contains("\"age\":36");
    }

    @Test
    void null_input_produces_empty_content_CallToolResult_without_structured_content() {
      CallToolResult out = structured.map(null);
      assertThat(out.isError()).isNull();
      assertThat(out.structuredContent()).isNull();
      assertThat(out.content()).isEmpty();
    }

    @Test
    void value_that_serializes_to_a_non_object_node_throws_illegal_state() {
      // Registration-time classifier guarantees a structured handler only pairs with object-shaped
      // schemas, so this should never happen in practice — but a custom Jackson serializer or an
      // unexpected subclass could still produce a non-object node at runtime, in which case the
      // mapper fails loudly rather than silently dropping structuredContent.
      assertThatThrownBy(() -> structured.map(List.of("not", "an", "object")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("structuredContent must be a JSON object");
    }
  }

  record Person(String name, int age) {}
}
