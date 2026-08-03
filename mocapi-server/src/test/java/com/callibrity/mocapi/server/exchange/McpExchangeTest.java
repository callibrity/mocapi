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
  class Trace_context {

    @Test
    void
        a_null_trace_context_passed_to_the_canonical_constructor_falls_back_to_none_not_a_live_npe() {
      // A caller invoking the 4-arg canonical constructor directly (bypassing the 3-arg convenience
      // overload, which always supplies TraceContext.NONE itself) with a literal null must still
      // get a safe, non-null TraceContext — never an NPE the first time traceContext() is read, and
      // never silently left null underneath a record that promises "never null" in its own javadoc.
      var exchange =
          new McpExchange(
              McpServer.PROTOCOL_VERSION,
              new Implementation("test-client", null, "1.0.0", null),
              null,
              null);

      assertThat(exchange.traceContext()).isSameAs(TraceContext.NONE);
    }

    @Test
    void an_explicitly_supplied_trace_context_is_kept_verbatim_not_replaced_by_the_null_default() {
      // If the canonical constructor's null-coalescing ever became unconditional, a transport that
      // extracted a real W3C traceparent from the request would have it silently discarded and
      // replaced with TraceContext.NONE — breaking distributed trace joining for every request
      // that actually carried trace context.
      var traceContext = new TraceContext("00-trace-01", "vendor=state", null);

      var exchange =
          new McpExchange(
              McpServer.PROTOCOL_VERSION,
              new Implementation("test-client", null, "1.0.0", null),
              null,
              traceContext);

      assertThat(exchange.traceContext()).isSameAs(traceContext);
    }
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
