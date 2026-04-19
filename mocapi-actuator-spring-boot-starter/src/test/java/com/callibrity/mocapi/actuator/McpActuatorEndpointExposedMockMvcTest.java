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
package com.callibrity.mocapi.actuator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = ActuatorMockMvcTestApp.class)
@TestPropertySource(properties = "management.endpoints.web.exposure.include=mcp")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpActuatorEndpointExposedMockMvcTest {

  @Autowired private WebApplicationContext context;

  @Test
  void get_actuator_mcp_returns_snapshot_payload_when_exposed() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    mockMvc
        .perform(get("/actuator/mcp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.server.name").value("mocapi"))
        .andExpect(jsonPath("$.server.version").value("9.9.9"))
        .andExpect(jsonPath("$.server.protocolVersion").value("2025-11-25"))
        .andExpect(jsonPath("$.counts.tools").value(1))
        .andExpect(jsonPath("$.counts.prompts").value(1))
        .andExpect(jsonPath("$.counts.resources").value(1))
        .andExpect(jsonPath("$.counts.resourceTemplates").value(1))
        .andExpect(jsonPath("$.tools[0].name").value("sample_tool"))
        .andExpect(jsonPath("$.prompts[0].name").value("sample_prompt"))
        .andExpect(jsonPath("$.resources[0].uri").value("docs://a"))
        .andExpect(jsonPath("$.resourceTemplates[0].uriTemplate").value("docs://{x}"));
  }
}
