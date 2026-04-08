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
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.client.ClientCapabilities;
import com.callibrity.mocapi.client.ClientInfo;
import com.callibrity.mocapi.client.ElicitationCapability;
import com.callibrity.mocapi.client.RootsCapability;
import com.callibrity.mocapi.client.SamplingCapability;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpServerTest {

  @Mock private McpServerCapability capability;

  private record TestCapabilityDescriptor(String value) implements CapabilityDescriptor {}

  @Test
  void constructorShouldInitializeServer() {
    var descriptor = new TestCapabilityDescriptor("test describe");

    when(capability.name()).thenReturn("test-capability");
    when(capability.describe()).thenReturn(descriptor);

    var server =
        new McpServer(
            List.of(capability),
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
    // test-capability + elicitation (client declared empty elicitation = form support)
    assertThat(response.capabilities()).hasSize(2);
    assertThat(response.capabilities()).containsEntry("test-capability", descriptor);
    assertThat(response.capabilities()).containsKey("elicitation");
    assertThat(response.serverInfo()).isNotNull();
    assertThat(response.serverInfo().name()).isEqualTo("Test Server");
    assertThat(response.serverInfo().title()).isEqualTo("The Test Server");
    assertThat(response.serverInfo().version()).isEqualTo("1.0.0");
  }

  @Test
  void pingShouldReturnResponse() {
    var descriptor = new TestCapabilityDescriptor("test describe");

    when(capability.name()).thenReturn("test-capability");
    when(capability.describe()).thenReturn(descriptor);

    var server =
        new McpServer(
            List.of(capability),
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");
    var response = server.ping();
    assertThat(response).isNotNull();
  }

  @Test
  void initializeShouldNotIncludeElicitationWhenClientDoesNotSupport() {
    var descriptor = new TestCapabilityDescriptor("test describe");

    when(capability.name()).thenReturn("test-capability");
    when(capability.describe()).thenReturn(descriptor);

    var server =
        new McpServer(
            List.of(capability),
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");

    var response =
        server.initialize(
            "123",
            new ClientCapabilities(null, null, null, null, null),
            new ClientInfo("Test Client", "A test client", "1.0.0", null, null, null));

    assertThat(response.capabilities()).hasSize(1);
    assertThat(response.capabilities()).containsEntry("test-capability", descriptor);
    assertThat(response.capabilities()).doesNotContainKey("elicitation");
  }

  @Test
  void clientInitializedShouldDoNothing() {
    var descriptor = new TestCapabilityDescriptor("test describe");

    when(capability.name()).thenReturn("test-capability");
    when(capability.describe()).thenReturn(descriptor);

    var server =
        new McpServer(
            List.of(capability),
            new ServerInfo("Test Server", "The Test Server", "1.0.0", null, null, null),
            "Testing instructions");
    assertThatNoException().isThrownBy(server::clientInitialized);
  }
}
