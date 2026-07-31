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
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UiMetaSerializationTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void tool_meta_serializes_resource_uri_and_visibility() {
    var meta = new McpUiToolMeta("ui://x", List.of("model", "app"));
    var node = mapper.valueToTree(meta);
    assertThat(node.path("resourceUri").asString()).isEqualTo("ui://x");
    assertThat(node.path("visibility").get(0).asString()).isEqualTo("model");
  }

  @Test
  void resource_meta_omits_empty_csp_fields() {
    var meta =
        new UiResourceMeta(
            new McpUiResourceCsp(List.of("https://api.example.com"), null, null, null), null);
    var node = mapper.valueToTree(meta);
    assertThat(node.path("csp").path("connectDomains").get(0).asString())
        .isEqualTo("https://api.example.com");
    assertThat(node.path("csp").has("resourceDomains")).isFalse();
    assertThat(node.has("sandbox")).isFalse();
  }
}
