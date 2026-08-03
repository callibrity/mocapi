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

import com.callibrity.mocapi.model.ServerCapabilities;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UiCapabilityCustomizerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void declares_the_ui_extension_with_the_mcp_app_html_mime_type() {
    ServerCapabilities.Builder builder = ServerCapabilities.builder();

    new UiCapabilityCustomizer(mapper).customize(builder);

    ServerCapabilities capabilities = builder.build();
    assertThat(capabilities.extensions()).containsKey("io.modelcontextprotocol/ui");

    ObjectNode expected = mapper.createObjectNode();
    ArrayNode mimeTypes = expected.putArray("mimeTypes");
    mimeTypes.add("text/html;profile=mcp-app");

    assertThat(capabilities.extensions().get("io.modelcontextprotocol/ui")).isEqualTo(expected);
  }
}
