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
import java.util.Map;
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
class PromptsGetWithArgsIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void getPromptWithArguments() throws Exception {
    String sessionId = client.initialize();

    var params =
        new GetPromptRequestParams(
            "test_prompt_with_arguments", Map.of("arg1", "hello", "arg2", "world"), null);

    JsonNode response =
        client.call(
            sessionId, "prompts/get", params, client.objectMapper().getNodeFactory().numberNode(2));

    JsonNode messages = response.get("result").get("messages");
    assertThat(messages.isArray()).isTrue();
    assertThat(messages.get(0).get("role").asString()).isEqualTo("user");
    assertThat(messages.get(0).get("content").get("type").asString()).isEqualTo("text");
    assertThat(messages.get(0).get("content").get("text").asString())
        .isEqualTo("Prompt with arguments: arg1='hello', arg2='world'");
  }
}
