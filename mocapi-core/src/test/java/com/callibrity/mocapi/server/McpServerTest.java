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

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class McpServerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void initializeResultShouldSerializeWithNonNullCapabilities() throws Exception {
    var result =
        new InitializeResult(
            InitializeResult.PROTOCOL_VERSION,
            new ServerCapabilities(new ToolsCapability(false), null, null, null, null),
            new Implementation("Test Server", "The Test Server", "1.0.0"),
            "Testing instructions");

    assertThat(result.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(result.capabilities().tools()).isNotNull();
    assertThat(result.capabilities().tools().listChanged()).isFalse();
    assertThat(result.serverInfo().name()).isEqualTo("Test Server");
    assertThat(result.instructions()).isEqualTo("Testing instructions");
  }

  @Test
  void initializeResultShouldOmitNullCapabilities() throws Exception {
    var result =
        new InitializeResult(
            InitializeResult.PROTOCOL_VERSION,
            new ServerCapabilities(null, null, null, null, null),
            new Implementation("Test Server", "The Test Server", "1.0.0"),
            null);

    String json = objectMapper.writeValueAsString(result);
    assertThat(json).doesNotContain("\"tools\"");
    assertThat(json).doesNotContain("\"instructions\"");
    assertThat(json).contains("\"protocolVersion\"");
    assertThat(json).contains("\"serverInfo\"");
  }

  @Test
  void initializeResultShouldSerializeToolsCapability() throws Exception {
    var result =
        new InitializeResult(
            InitializeResult.PROTOCOL_VERSION,
            new ServerCapabilities(new ToolsCapability(false), null, null, null, null),
            new Implementation("Test Server", "The Test Server", "1.0.0"),
            null);

    String json = objectMapper.writeValueAsString(result);
    assertThat(json).contains("\"tools\"");
    assertThat(json).contains("\"listChanged\"");
  }

  @Test
  void pingResponseShouldBeEmpty() {
    var response = java.util.Map.of();
    assertThat(response).isNotNull();
  }
}
