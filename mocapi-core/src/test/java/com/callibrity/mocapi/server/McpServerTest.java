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

import com.callibrity.mocapi.client.ClientCapabilities;
import com.callibrity.mocapi.client.ClientInfo;
import com.callibrity.mocapi.client.ElicitationCapability;
import com.callibrity.mocapi.client.RootsCapability;
import com.callibrity.mocapi.client.SamplingCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class McpServerTest {

    @Mock
    private McpServerCapability capability;

    @Test
    void constructorShouldInitializeServer() {

        when(capability.name()).thenReturn("test-capability");
        when(capability.describe()).thenReturn("test describe");

        var server = new McpServer(List.of(capability), new ServerInfo("Test Server", "The Test Server", "1.0.0"), "Testing instructions");

        var response = server.initialize("123", new ClientCapabilities(new RootsCapability(true), new SamplingCapability(), new ElicitationCapability()), new ClientInfo("Test Client", "A test client", "1.0.0"));

        assertThat(response).isNotNull();
        assertThat(response.protocolVersion()).isEqualTo("2025-06-18");
        assertThat(response.capabilities()).isNotNull();
        assertThat(response.capabilities()).hasSize(1);
        assertThat(response.capabilities()).containsEntry("test-capability", "test describe");
        assertThat(response.serverInfo()).isNotNull();
        assertThat(response.serverInfo().name()).isEqualTo("Test Server");
        assertThat(response.serverInfo().title()).isEqualTo("The Test Server");
        assertThat(response.serverInfo().version()).isEqualTo("1.0.0");
    }

    @Test
    void pingShouldReturnResponse() {
        when(capability.name()).thenReturn("test-capability");
        when(capability.describe()).thenReturn("test describe");

        var server = new McpServer(List.of(capability), new ServerInfo("Test Server", "The Test Server", "1.0.0"), "Testing instructions");
        var response = server.ping();
        assertThat(response).isNotNull();
    }

    @Test
    void clientInitializedShouldDoNothing() {
        when(capability.name()).thenReturn("test-capability");
        when(capability.describe()).thenReturn("test describe");

        var server = new McpServer(List.of(capability), new ServerInfo("Test Server", "The Test Server", "1.0.0"), "Testing instructions");
        assertThatNoException().isThrownBy(server::clientInitialized);
    }
}