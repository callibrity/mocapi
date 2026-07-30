/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.McpServer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpExchangeTest {

  private static McpExchange exchangeWith(ClientCapabilities capabilities) {
    return new McpExchange(
        McpServer.PROTOCOL_VERSION,
        new Implementation("test-client", null, "1.0.0", null),
        capabilities);
  }

  @Nested
  class Supports_elicitation_form {

    @Test
    void is_false_when_client_capabilities_are_null() {
      assertThat(exchangeWith(null).supportsElicitationForm()).isFalse();
    }

    @Test
    void is_false_when_elicitation_capability_is_absent() {
      var capabilities = new ClientCapabilities(null, null, null, null, null);

      assertThat(exchangeWith(capabilities).supportsElicitationForm()).isFalse();
    }

    @Test
    void is_true_for_bare_elicitation_capability() {
      // A bare {} still means form-capable: presence of the capability is the signal.
      var capabilities =
          new ClientCapabilities(null, null, null, new ElicitationCapability(null, null), null);

      assertThat(exchangeWith(capabilities).supportsElicitationForm()).isTrue();
    }

    @Test
    void is_true_when_form_sub_object_is_declared() {
      var elicitation = new ElicitationCapability(JsonNodeFactory.instance.objectNode(), null);
      var capabilities = new ClientCapabilities(null, null, null, elicitation, null);

      assertThat(exchangeWith(capabilities).supportsElicitationForm()).isTrue();
    }
  }
}
