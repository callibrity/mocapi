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

import com.callibrity.mocapi.model.GetPromptRequestParams;
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
class PromptsGetWithImageIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void getPromptWithImage() throws Exception {
    String sessionId = client.initialize();

    var params = new GetPromptRequestParams("test_prompt_with_image", null, null);

    JsonNode response =
        client.call(
            sessionId, "prompts/get", params, client.objectMapper().getNodeFactory().numberNode(2));

    JsonNode messages = response.get("result").get("messages");
    assertThat(messages.isArray()).isTrue();

    JsonNode msg0 = messages.get(0);
    assertThat(msg0.get("role").asString()).isEqualTo("user");
    assertThat(msg0.get("content").get("type").asString()).isEqualTo("image");
    assertThat(msg0.get("content").get("mimeType").asString()).isEqualTo("image/png");
    assertThat(msg0.get("content").get("data").asString())
        .isEqualTo(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVQI12P4z8AAAAACAAHiIbwzAAAAAElFTkSuQmCC");

    JsonNode msg1 = messages.get(1);
    assertThat(msg1.get("role").asString()).isEqualTo("user");
    assertThat(msg1.get("content").get("type").asString()).isEqualTo("text");
    assertThat(msg1.get("content").get("text").asString())
        .isEqualTo("Please analyze the image above.");
  }
}
