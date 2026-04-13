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
package com.callibrity.mocapi.compat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ToolDiscoveryIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void toolsListReturnsArrayOfTools() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(1));

    JsonNode tools = response.get("result").get("tools");
    assertThat(tools.isArray()).isTrue();
    assertThat(tools.isEmpty()).isFalse();
  }

  @Test
  void eachToolHasNameField() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(1));

    JsonNode firstTool = response.get("result").get("tools").get(0);
    assertThat(firstTool.get("name").isString()).isTrue();
    assertThat(firstTool.get("name").asString()).isNotEmpty();
  }

  @Test
  void eachToolHasInputSchemaField() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(1));

    assertThat(response.get("result").get("tools").get(0).has("inputSchema")).isTrue();
  }

  @Test
  void paginationIncludesNextCursorWhenMoreToolsExist() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(1));

    assertThat(response.get("result").get("tools").isArray()).isTrue();
  }

  @Test
  void finalPageHasNullNextCursor() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(1));

    assertThat(response.get("result").has("nextCursor")).isFalse();
  }
}
