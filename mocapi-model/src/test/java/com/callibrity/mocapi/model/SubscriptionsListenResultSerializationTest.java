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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SubscriptionsListenResultSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void round_trip_with_result_type_and_subscription_id() throws Exception {
    var result =
        new SubscriptionsListenResult(
            new SubscriptionsListenResultMetaObject("sub-1"), ResultTypes.COMPLETE);
    String json = mapper.writeValueAsString(result);
    assertThat(json)
        .contains("\"resultType\":\"complete\"")
        .contains("\"_meta\":{\"io.modelcontextprotocol/subscriptionId\":\"sub-1\"}");

    var deserialized = mapper.readValue(json, SubscriptionsListenResult.class);
    assertThat(deserialized.resultType()).isEqualTo(ResultTypes.COMPLETE);
    assertThat(deserialized.meta().subscriptionId()).isEqualTo("sub-1");
  }
}
