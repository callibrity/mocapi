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

import java.util.ArrayList;
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
class ResourcesListIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void resourcesListReturnsAllConformanceResources() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId,
            "resources/list",
            null,
            client.objectMapper().getNodeFactory().numberNode(2));

    JsonNode resources = response.get("result").get("resources");
    assertThat(resources.isArray()).isTrue();

    var uris = new ArrayList<String>();
    for (var resource : resources) {
      uris.add(resource.get("uri").asString());
    }

    assertThat(uris)
        .contains("test://static-text", "test://static-binary", "test://watched-resource");
  }

  @Test
  void resourcesTemplatesListReturnsTemplates() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(
            sessionId,
            "resources/templates/list",
            null,
            client.objectMapper().getNodeFactory().numberNode(2));

    JsonNode templates = response.get("result").get("resourceTemplates");
    assertThat(templates.isArray()).isTrue();

    var uriTemplates = new ArrayList<String>();
    for (var template : templates) {
      uriTemplates.add(template.get("uriTemplate").asString());
    }

    assertThat(uriTemplates).contains("test://template/{id}/data");
  }
}
