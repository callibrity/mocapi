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
package com.callibrity.mocapi.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RootsCapability;
import com.callibrity.mocapi.model.SamplingCapability;
import org.junit.jupiter.api.Test;

class McpSessionTest {

  @Test
  void twoArgConstructorDefaultsToWarningAndNotInitialized() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.logLevel()).isEqualTo(LoggingLevel.WARNING);
    assertThat(session.initialized()).isFalse();
  }

  @Test
  void threeArgConstructorDefaultsToNotInitialized() {
    var session = new McpSession("s1", "2025-11-25", null, null, LoggingLevel.DEBUG);

    assertThat(session.logLevel()).isEqualTo(LoggingLevel.DEBUG);
    assertThat(session.initialized()).isFalse();
  }

  @Test
  void withLogLevelReturnsNewSessionWithUpdatedLevel() {
    var session = new McpSession("s1", "2025-11-25", null, null);
    var updated = session.withLogLevel(LoggingLevel.ERROR);

    assertThat(updated.logLevel()).isEqualTo(LoggingLevel.ERROR);
    assertThat(updated.sessionId()).isEqualTo("s1");
    assertThat(updated.initialized()).isFalse();
  }

  @Test
  void withInitializedReturnsNewSessionWithUpdatedFlag() {
    var session = new McpSession("s1", "2025-11-25", null, null);
    var updated = session.withInitialized(true);

    assertThat(updated.initialized()).isTrue();
    assertThat(updated.sessionId()).isEqualTo("s1");
    assertThat(updated.logLevel()).isEqualTo(LoggingLevel.WARNING);
  }

  @Test
  void supportsElicitationFormReturnsTrueWhenCapabilityPresent() {
    var capabilities = new ClientCapabilities(null, null, new ElicitationCapability());
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsElicitationForm()).isTrue();
  }

  @Test
  void supportsElicitationFormReturnsFalseWhenCapabilityAbsent() {
    var capabilities = new ClientCapabilities(null, null, null);
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsElicitationForm()).isFalse();
  }

  @Test
  void supportsElicitationFormReturnsFalseWhenCapabilitiesNull() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.supportsElicitationForm()).isFalse();
  }

  @Test
  void supportsSamplingReturnsTrueWhenCapabilityPresent() {
    var capabilities = new ClientCapabilities(null, new SamplingCapability(), null);

    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsSampling()).isTrue();
  }

  @Test
  void supportsSamplingReturnsFalseWhenCapabilityAbsent() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.supportsSampling()).isFalse();
  }

  @Test
  void supportsRootsReturnsTrueWhenCapabilityPresent() {
    var capabilities = new ClientCapabilities(new RootsCapability(true), null, null);
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsRoots()).isTrue();
  }

  @Test
  void supportsRootsReturnsFalseWhenCapabilityAbsent() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.supportsRoots()).isFalse();
  }

  @Test
  void sessionPreservesClientInfo() {
    var clientInfo = new Implementation("test-client", "Test", "1.0");
    var session = new McpSession("s1", "2025-11-25", null, clientInfo);

    assertThat(session.clientInfo()).isSameAs(clientInfo);
  }
}
