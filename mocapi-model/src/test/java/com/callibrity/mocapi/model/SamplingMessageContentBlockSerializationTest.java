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
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Round-trip fidelity for the sampling-only content blocks ({@code SamplingMessageContentBlock}
 * union members not shared with the plain {@code ContentBlock} union). Kept per the spec's SEP-2577
 * deprecation window rather than removed (ADR-0014, mocapi-model mirrors schema.ts 1:1).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
// SEP-2577 spec contract: ToolUseContent/ToolResultContent stay in the sampling content-block
// union for at least twelve months, and this suite must exercise the deprecated types directly to
// keep 1:1 union fidelity with schema.ts under test (ADR-0014).
@SuppressWarnings("deprecation")
class SamplingMessageContentBlockSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void tool_use_content_round_trip() throws Exception {
    ObjectNode input = mapper.createObjectNode().put("city", "Columbus");
    var original = new ToolUseContent("call-1", "get_weather", input, null);

    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"tool_use\"").contains("\"name\":\"get_weather\"");

    SamplingMessageContentBlock deserialized =
        mapper.readValue(json, SamplingMessageContentBlock.class);
    assertThat(deserialized).isInstanceOf(ToolUseContent.class);
    var toolUse = (ToolUseContent) deserialized;
    assertThat(toolUse.id()).isEqualTo("call-1");
    assertThat(toolUse.input().path("city").asString()).isEqualTo("Columbus");
  }

  @Test
  void tool_result_content_round_trip() throws Exception {
    var content = List.of((ContentBlock) new TextContent("72F and sunny", null));
    var original = new ToolResultContent("call-1", content, null, false, null);

    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"tool_result\"").contains("\"toolUseId\":\"call-1\"");

    SamplingMessageContentBlock deserialized =
        mapper.readValue(json, SamplingMessageContentBlock.class);
    assertThat(deserialized).isInstanceOf(ToolResultContent.class);
    var toolResult = (ToolResultContent) deserialized;
    assertThat(toolResult.toolUseId()).isEqualTo("call-1");
    assertThat(toolResult.isError()).isFalse();
    assertThat(toolResult.content()).hasSize(1);
  }

  @Test
  void tool_result_content_marked_as_an_error_round_trips_the_error_flag() throws Exception {
    var original = new ToolResultContent("call-2", List.of(), null, true, null);

    String json = mapper.writeValueAsString(original);

    SamplingMessageContentBlock deserialized =
        mapper.readValue(json, SamplingMessageContentBlock.class);
    assertThat(((ToolResultContent) deserialized).isError()).isTrue();
  }
}
