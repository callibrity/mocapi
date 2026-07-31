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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ToolTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omits_meta_when_absent() {
    Tool tool = new Tool("t", "T", "desc", mapper.createObjectNode(), null);
    assertThat(mapper.valueToTree(tool).has("_meta")).isFalse();
  }

  @Test
  void serializes_meta_under_underscore_meta_key() {
    ObjectNode meta = mapper.createObjectNode();
    meta.putObject("ui").put("resourceUri", "ui://x");
    Tool tool = new Tool("t", "T", "desc", mapper.createObjectNode(), null).withMeta(meta);
    assertThat(mapper.valueToTree(tool).path("_meta").path("ui").path("resourceUri").asString())
        .isEqualTo("ui://x");
  }
}
