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

import com.callibrity.mocapi.model.ResourceRequestParams;
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
class ResourcesTemplatesReadIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void readTemplateResourceSubstitutesId() throws Exception {
    String sessionId = client.initialize();

    var params = new ResourceRequestParams("test://template/123/data", null);

    JsonNode response =
        client.call(
            sessionId,
            "resources/read",
            params,
            client.objectMapper().getNodeFactory().numberNode(2));

    JsonNode contents = response.get("result").get("contents");
    assertThat(contents.isArray()).isTrue();
    assertThat(contents.get(0).get("uri").asString()).isEqualTo("test://template/123/data");
    assertThat(contents.get(0).get("mimeType").asString()).isEqualTo("application/json");
    assertThat(contents.get(0).get("text").asString())
        .isEqualTo("{\"id\":\"123\",\"templateTest\":true,\"data\":\"Data for ID: 123\"}");
  }
}
