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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpSessionTest {

  @Test
  void two_arg_constructor_defaults_to_warning_and_not_initialized() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.logLevel()).isEqualTo(LoggingLevel.WARNING);
    assertThat(session.initialized()).isFalse();
  }

  @Test
  void three_arg_constructor_defaults_to_not_initialized() {
    var session = new McpSession("s1", "2025-11-25", null, null, LoggingLevel.DEBUG);

    assertThat(session.logLevel()).isEqualTo(LoggingLevel.DEBUG);
    assertThat(session.initialized()).isFalse();
  }

  @Test
  void with_log_level_returns_new_session_with_updated_level() {
    var session = new McpSession("s1", "2025-11-25", null, null);
    var updated = session.withLogLevel(LoggingLevel.ERROR);

    assertThat(updated.logLevel()).isEqualTo(LoggingLevel.ERROR);
    assertThat(updated.sessionId()).isEqualTo("s1");
    assertThat(updated.initialized()).isFalse();
  }

  @Test
  void with_initialized_returns_new_session_with_updated_flag() {
    var session = new McpSession("s1", "2025-11-25", null, null);
    var updated = session.withInitialized(true);

    assertThat(updated.initialized()).isTrue();
    assertThat(updated.sessionId()).isEqualTo("s1");
    assertThat(updated.logLevel()).isEqualTo(LoggingLevel.WARNING);
  }

  @Test
  void supports_elicitation_form_returns_true_when_capability_present() {
    var capabilities = new ClientCapabilities(null, null, new ElicitationCapability());
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsElicitationForm()).isTrue();
  }

  @Test
  void supports_elicitation_form_returns_false_when_capability_absent() {
    var capabilities = new ClientCapabilities(null, null, null);
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsElicitationForm()).isFalse();
  }

  @Test
  void supports_elicitation_form_returns_false_when_capabilities_null() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.supportsElicitationForm()).isFalse();
  }

  @Test
  void supports_sampling_returns_true_when_capability_present() {
    var capabilities = new ClientCapabilities(null, new SamplingCapability(), null);

    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsSampling()).isTrue();
  }

  @Test
  void supports_sampling_returns_false_when_capability_absent() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.supportsSampling()).isFalse();
  }

  @Test
  void supports_roots_returns_true_when_capability_present() {
    var capabilities = new ClientCapabilities(new RootsCapability(true), null, null);
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsRoots()).isTrue();
  }

  @Test
  void supports_roots_returns_false_when_capability_absent() {
    var session = new McpSession("s1", "2025-11-25", null, null);

    assertThat(session.supportsRoots()).isFalse();
  }

  @Test
  void supports_sampling_returns_false_when_sampling_null() {
    var capabilities = new ClientCapabilities(null, null, null);
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsSampling()).isFalse();
  }

  @Test
  void supports_roots_returns_false_when_roots_null() {
    var capabilities = new ClientCapabilities(null, null, null);
    var session = new McpSession("s1", "2025-11-25", capabilities, null);

    assertThat(session.supportsRoots()).isFalse();
  }

  @Test
  void session_preserves_client_info() {
    var clientInfo = new Implementation("test-client", "Test", "1.0");
    var session = new McpSession("s1", "2025-11-25", null, clientInfo);

    assertThat(session.clientInfo()).isSameAs(clientInfo);
  }
}
