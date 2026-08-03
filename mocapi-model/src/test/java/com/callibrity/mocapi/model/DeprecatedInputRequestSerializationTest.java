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

/**
 * Round-trip fidelity for the deprecated {@link InputRequest} union members that mocapi never emits
 * itself but must still deserialize/serialize correctly for 1:1 union completeness with the spec
 * (I7, ADR-0014). Retained per SEP-2577's twelve-month deprecation window.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
// SEP-2577 spec contract: CreateMessageRequest and ListRootsRequest stay in the InputRequest union
// for at least twelve months, and this suite must exercise the deprecated types directly to keep
// 1:1 union fidelity with schema.ts under test (ADR-0014).
@SuppressWarnings("deprecation")
class DeprecatedInputRequestSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void create_message_request_round_trips_through_the_input_request_union() throws Exception {
    var message = new SamplingMessage(Role.USER, List.of(new TextContent("hi", null)), null);
    var params =
        new CreateMessageRequestParams(
            List.of(message), null, null, null, null, 100, null, null, null, null);
    var original = new CreateMessageRequest(params);

    String json = mapper.writeValueAsString((InputRequest) original);
    assertThat(json).contains("\"method\":\"sampling/createMessage\"");

    InputRequest deserialized = mapper.readValue(json, InputRequest.class);
    assertThat(deserialized).isInstanceOf(CreateMessageRequest.class);
    var roundTripped = (CreateMessageRequest) deserialized;
    assertThat(roundTripped.params().maxTokens()).isEqualTo(100);
    assertThat(roundTripped.params().messages()).hasSize(1);
  }

  @Test
  void list_roots_request_round_trips_through_the_input_request_union() throws Exception {
    var original = new ListRootsRequest(null);

    String json = mapper.writeValueAsString((InputRequest) original);
    assertThat(json).contains("\"method\":\"roots/list\"");

    InputRequest deserialized = mapper.readValue(json, InputRequest.class);
    assertThat(deserialized).isInstanceOf(ListRootsRequest.class);
  }

  @Test
  void list_roots_request_carries_optional_request_params_when_present() throws Exception {
    var original = new ListRootsRequest(new RequestParams(null));

    String json = mapper.writeValueAsString((InputRequest) original);

    InputRequest deserialized = mapper.readValue(json, InputRequest.class);
    assertThat(((ListRootsRequest) deserialized).params()).isNotNull();
  }
}
