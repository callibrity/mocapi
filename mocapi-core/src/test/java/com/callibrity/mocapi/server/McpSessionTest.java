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

import com.callibrity.mocapi.client.ClientCapabilities;
import com.callibrity.mocapi.client.ClientInfo;
import com.callibrity.mocapi.client.ElicitationCapability;
import com.callibrity.mocapi.client.RootsCapability;
import com.callibrity.mocapi.client.SamplingCapability;
import org.junit.jupiter.api.Test;

class McpSessionTest {

  private static final ClientInfo CLIENT_INFO =
      new ClientInfo("test-client", null, "1.0", null, null, null);

  @Test
  void shouldStoreProtocolVersionAndClientInfo() {
    var session =
        new McpSession(
            "2025-11-25", new ClientCapabilities(null, null, null, null, null), CLIENT_INFO);
    assertThat(session.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(session.clientInfo().name()).isEqualTo("test-client");
  }

  @Test
  void supportsElicitationFormShouldReturnTrueWhenFormPresent() {
    var caps =
        new ClientCapabilities(
            null,
            null,
            new ElicitationCapability(new ElicitationCapability.FormCapability(), null),
            null,
            null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsElicitationForm()).isTrue();
  }

  @Test
  void supportsElicitationFormShouldReturnTrueForEmptyElicitation() {
    var caps =
        new ClientCapabilities(null, null, new ElicitationCapability(null, null), null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsElicitationForm()).isTrue();
  }

  @Test
  void supportsElicitationFormShouldReturnFalseWhenElicitationNull() {
    var caps = new ClientCapabilities(null, null, null, null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsElicitationForm()).isFalse();
  }

  @Test
  void supportsElicitationFormShouldReturnFalseWhenCapabilitiesNull() {
    var session = new McpSession("2025-11-25", null, CLIENT_INFO);
    assertThat(session.supportsElicitationForm()).isFalse();
  }

  @Test
  void supportsElicitationUrlShouldReturnTrueWhenUrlPresent() {
    var caps =
        new ClientCapabilities(
            null,
            null,
            new ElicitationCapability(null, new ElicitationCapability.UrlCapability()),
            null,
            null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsElicitationUrl()).isTrue();
  }

  @Test
  void supportsElicitationUrlShouldReturnFalseWhenUrlAbsent() {
    var caps =
        new ClientCapabilities(null, null, new ElicitationCapability(null, null), null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsElicitationUrl()).isFalse();
  }

  @Test
  void supportsSamplingShouldReturnTrueWhenPresent() {
    var caps = new ClientCapabilities(null, new SamplingCapability(), null, null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsSampling()).isTrue();
  }

  @Test
  void supportsSamplingShouldReturnFalseWhenAbsent() {
    var caps = new ClientCapabilities(null, null, null, null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsSampling()).isFalse();
  }

  @Test
  void supportsRootsShouldReturnTrueWhenPresent() {
    var caps = new ClientCapabilities(new RootsCapability(true), null, null, null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsRoots()).isTrue();
  }

  @Test
  void supportsRootsShouldReturnFalseWhenAbsent() {
    var caps = new ClientCapabilities(null, null, null, null, null);
    var session = new McpSession("2025-11-25", caps, CLIENT_INFO);
    assertThat(session.supportsRoots()).isFalse();
  }
}
