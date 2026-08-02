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

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClientCapabilitiesTest {

  private static final String EXTENSION_ID = "io.modelcontextprotocol/tasks";

  private static ClientCapabilities withExtensions(Map<String, ObjectNode> extensions) {
    return new ClientCapabilities(null, null, null, null, extensions);
  }

  @Test
  void true_when_the_extension_is_declared() {
    ClientCapabilities caps =
        withExtensions(Map.of(EXTENSION_ID, JsonNodeFactory.instance.objectNode()));

    assertThat(caps.hasExtension(EXTENSION_ID)).isTrue();
  }

  @Test
  void false_when_extensions_map_is_empty() {
    ClientCapabilities caps = withExtensions(Map.of());

    assertThat(caps.hasExtension(EXTENSION_ID)).isFalse();
  }

  @Test
  void false_when_extensions_map_lacks_the_id() {
    ClientCapabilities caps =
        withExtensions(
            Map.of("io.modelcontextprotocol/other", JsonNodeFactory.instance.objectNode()));

    assertThat(caps.hasExtension(EXTENSION_ID)).isFalse();
  }

  @Test
  void false_when_extensions_map_is_null() {
    ClientCapabilities caps = withExtensions(null);

    assertThat(caps.hasExtension(EXTENSION_ID)).isFalse();
  }
}
