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
package com.callibrity.mocapi.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class McpServerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void initializeResponseShouldSerializeWithNonNullCapabilities() throws Exception {
    var response =
        new InitializeResponse(
            InitializeResponse.PROTOCOL_VERSION,
            new ServerCapabilities(new ToolsCapabilityDescriptor(false)),
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");

    assertThat(response.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(response.capabilities().tools()).isNotNull();
    assertThat(response.capabilities().tools().listChanged()).isFalse();
    assertThat(response.serverInfo().name()).isEqualTo("Test Server");
    assertThat(response.instructions()).isEqualTo("Testing instructions");
  }

  @Test
  void initializeResponseShouldOmitNullCapabilities() throws Exception {
    var response =
        new InitializeResponse(
            InitializeResponse.PROTOCOL_VERSION,
            new ServerCapabilities(null),
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            null);

    String json = objectMapper.writeValueAsString(response);
    assertThat(json).doesNotContain("\"tools\"");
    assertThat(json).doesNotContain("\"instructions\"");
    assertThat(json).contains("\"protocolVersion\"");
    assertThat(json).contains("\"serverInfo\"");
  }

  @Test
  void initializeResponseShouldSerializeToolsCapability() throws Exception {
    var response =
        new InitializeResponse(
            InitializeResponse.PROTOCOL_VERSION,
            new ServerCapabilities(new ToolsCapabilityDescriptor(false)),
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            null);

    String json = objectMapper.writeValueAsString(response);
    assertThat(json).contains("\"tools\"");
    assertThat(json).contains("\"listChanged\"");
  }

  @Test
  void pingResponseShouldBeEmpty() {
    var response = new PingResponse();
    assertThat(response).isNotNull();
  }
}
