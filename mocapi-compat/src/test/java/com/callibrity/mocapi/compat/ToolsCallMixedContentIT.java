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

import com.callibrity.mocapi.model.CallToolRequestParams;
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
class ToolsCallMixedContentIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void callMixedContentReturnsMultipleTypes() throws Exception {
    String sessionId = client.initialize();

    var arguments = client.objectMapper().createObjectNode();
    var params = new CallToolRequestParams("test_multiple_content_types", arguments, null, null);

    JsonNode response =
        client.call(
            sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(2));

    JsonNode content = response.get("result").get("content");
    assertThat(content.size()).isEqualTo(3);

    assertThat(content.get(0).get("type").asString()).isEqualTo("text");
    assertThat(content.get(0).get("text").asString()).isEqualTo("Multiple content types test:");

    assertThat(content.get(1).get("type").asString()).isEqualTo("image");
    assertThat(content.get(1).get("mimeType").asString()).isEqualTo("image/png");
    assertThat(content.get(1).get("data").asString())
        .isEqualTo(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVQI12P4z8AAAAACAAHiIbwzAAAAAElFTkSuQmCC");

    assertThat(content.get(2).get("type").asString()).isEqualTo("resource");
    assertThat(content.get(2).get("resource").get("uri").asString())
        .isEqualTo("test://mixed-content-resource");
    assertThat(content.get(2).get("resource").get("mimeType").asString())
        .isEqualTo("application/json");
    assertThat(content.get(2).get("resource").get("text").asString())
        .isEqualTo("{\"test\":\"data\",\"value\":123}");
  }
}
