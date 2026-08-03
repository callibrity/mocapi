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

class ResourceTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omits_meta_when_absent() {
    Resource r = new Resource("ui://x", "X", "desc", "text/html;profile=mcp-app");
    assertThat(mapper.valueToTree(r).has("_meta")).isFalse();
  }

  @Test
  void serializes_meta_under_underscore_meta_key() {
    ObjectNode meta = mapper.createObjectNode();
    meta.putObject("ui").putObject("csp").putArray("connectDomains").add("https://api.example.com");
    Resource r = new Resource("ui://x", "X", "desc", "text/html;profile=mcp-app").withMeta(meta);
    assertThat(
            mapper
                .valueToTree(r)
                .path("_meta")
                .path("ui")
                .path("csp")
                .path("connectDomains")
                .get(0)
                .asString())
        .isEqualTo("https://api.example.com");
  }

  @Test
  void withMeta_deep_copies_so_later_mutation_of_the_input_node_does_not_leak_through() {
    ObjectNode meta = mapper.createObjectNode().put("k", "original");
    Resource r = new Resource("ui://x", "X", "desc", "text/html").withMeta(meta);

    meta.put("k", "mutated-after-build");

    assertThat(r.meta().path("k").asString()).isEqualTo("original");
  }

  @Test
  void withMeta_null_clears_a_previously_set_meta_while_leaving_other_fields_unchanged() {
    ObjectNode meta = mapper.createObjectNode().put("k", "v");
    Resource withMeta = new Resource("ui://x", "X", "desc", "text/html").withMeta(meta);

    Resource cleared = withMeta.withMeta(null);

    assertThat(cleared.meta()).isNull();
    assertThat(cleared.uri()).isEqualTo("ui://x");
    assertThat(cleared.name()).isEqualTo("X");
    assertThat(cleared.description()).isEqualTo("desc");
    assertThat(cleared.mimeType()).isEqualTo("text/html");
  }
}
