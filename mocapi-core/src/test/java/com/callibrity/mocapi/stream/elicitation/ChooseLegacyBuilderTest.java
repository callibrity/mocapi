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
package com.callibrity.mocapi.stream.elicitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ChooseLegacyBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldProduceEnumWithEnumNames() {
    ObjectNode node =
        new ChooseLegacyBuilder(
                List.of("opt1", "opt2", "opt3"),
                List.of("Option One", "Option Two", "Option Three"))
            .build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("string");
    assertThat(node.get("enum")).hasSize(3);
    assertThat(node.get("enum").get(0).asString()).isEqualTo("opt1");
    assertThat(node.get("enum").get(1).asString()).isEqualTo("opt2");
    assertThat(node.get("enum").get(2).asString()).isEqualTo("opt3");
    assertThat(node.get("enumNames")).hasSize(3);
    assertThat(node.get("enumNames").get(0).asString()).isEqualTo("Option One");
    assertThat(node.get("enumNames").get(1).asString()).isEqualTo("Option Two");
    assertThat(node.get("enumNames").get(2).asString()).isEqualTo("Option Three");
  }
}
