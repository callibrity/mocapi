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
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.ElicitationCapability;
import com.callibrity.mocapi.session.RootsCapability;
import com.callibrity.mocapi.session.SamplingCapability;
import com.callibrity.mocapi.tools.ToolsRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpServerTest {

  @Test
  void constructorShouldInitializeServerWithTools() {
    var toolsRegistry = new ToolsRegistry(List.of(), 50);

    var server =
        new McpServer(
            toolsRegistry,
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");

    var response =
        server.initialize(
            "123",
            new ClientCapabilities(
                new RootsCapability(true),
                new SamplingCapability(),
                new ElicitationCapability(null, null),
                null,
                null),
            new ClientInfo("Test Client", "A test client", "1.0.0", null, null, null));

    assertThat(response).isNotNull();
    assertThat(response.protocolVersion()).isEqualTo(McpServer.PROTOCOL_VERSION);
    assertThat(response.capabilities()).isNotNull();
    // tools + elicitation (client declared empty elicitation = form support)
    assertThat(response.capabilities()).hasSize(2);
    assertThat(response.capabilities()).containsKey("tools");
    assertThat(response.capabilities()).containsKey("elicitation");
    assertThat(response.serverInfo()).isNotNull();
    assertThat(response.serverInfo().name()).isEqualTo("Test Server");
    assertThat(response.serverInfo().title()).isEqualTo("The Test Server");
    assertThat(response.serverInfo().version()).isEqualTo("1.0.0");
  }

  @Test
  void constructorShouldInitializeServerWithoutTools() {
    var server =
        new McpServer(
            null,
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");

    var response =
        server.initialize(
            "123",
            new ClientCapabilities(null, null, null, null, null),
            new ClientInfo("Test Client", "A test client", "1.0.0", null, null, null));

    assertThat(response.capabilities()).isEmpty();
  }

  @Test
  void pingShouldReturnResponse() {
    var server =
        new McpServer(
            null,
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");
    var response = server.ping();
    assertThat(response).isNotNull();
  }

  @Test
  void initializeShouldNotIncludeElicitationWhenClientDoesNotSupport() {
    var toolsRegistry = new ToolsRegistry(List.of(), 50);

    var server =
        new McpServer(
            toolsRegistry,
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");

    var response =
        server.initialize(
            "123",
            new ClientCapabilities(null, null, null, null, null),
            new ClientInfo("Test Client", "A test client", "1.0.0", null, null, null));

    assertThat(response.capabilities()).hasSize(1);
    assertThat(response.capabilities()).containsKey("tools");
    assertThat(response.capabilities()).doesNotContainKey("elicitation");
  }

  @Test
  void clientInitializedShouldDoNothing() {
    var server =
        new McpServer(
            null,
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");
    assertThatNoException().isThrownBy(server::clientInitialized);
  }
}
