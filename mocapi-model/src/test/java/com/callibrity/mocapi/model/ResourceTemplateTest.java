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

class ResourceTemplateTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omits_meta_when_absent() {
    ResourceTemplate template = new ResourceTemplate("res://{id}", "R", "desc", "text/plain");
    assertThat(mapper.valueToTree(template).has("_meta")).isFalse();
  }

  @Test
  void serializes_meta_under_underscore_meta_key() {
    ObjectNode meta = mapper.createObjectNode().put("k", "v");
    ResourceTemplate template =
        new ResourceTemplate("res://{id}", "R", "desc", "text/plain").withMeta(meta);
    assertThat(mapper.valueToTree(template).path("_meta").path("k").asString()).isEqualTo("v");
  }

  @Test
  void withMeta_deep_copies_so_later_mutation_of_the_input_node_does_not_leak_through() {
    ObjectNode meta = mapper.createObjectNode().put("k", "original");
    ResourceTemplate template =
        new ResourceTemplate("res://{id}", "R", "desc", "text/plain").withMeta(meta);

    meta.put("k", "mutated-after-build");

    assertThat(template.meta().path("k").asString()).isEqualTo("original");
  }

  @Test
  void withMeta_null_clears_a_previously_set_meta_while_leaving_other_fields_unchanged() {
    ObjectNode meta = mapper.createObjectNode().put("k", "v");
    ResourceTemplate withMeta =
        new ResourceTemplate("res://{id}", "R", "desc", "text/plain").withMeta(meta);

    ResourceTemplate cleared = withMeta.withMeta(null);

    assertThat(cleared.meta()).isNull();
    assertThat(cleared.uriTemplate()).isEqualTo("res://{id}");
    assertThat(cleared.name()).isEqualTo("R");
    assertThat(cleared.description()).isEqualTo("desc");
    assertThat(cleared.mimeType()).isEqualTo("text/plain");
  }
}
