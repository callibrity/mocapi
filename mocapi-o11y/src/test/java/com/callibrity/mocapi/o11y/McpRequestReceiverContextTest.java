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
package com.callibrity.mocapi.o11y;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.server.exchange.TraceContext;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpRequestReceiverContextTest {

  private static final TraceContext CARRIER =
      new TraceContext("00-trace-01", "vendor=state", "userId=alice");

  @Test
  void a_propagator_reading_baggage_gets_the_clients_baggage_header_not_a_dropped_value() {
    // Micrometer Tracing's propagating handler reads traceparent/tracestate/baggage through this
    // getter to join the client's trace. If the baggage case were missing from the switch, a
    // tracing-capable registry would silently lose baggage propagation for every MCP request even
    // though the client supplied it.
    var context = new McpRequestReceiverContext(CARRIER);

    assertThat(context.getGetter().get(CARRIER, "baggage")).isEqualTo("userId=alice");
  }

  @Test
  void an_unrecognized_carrier_key_returns_null_instead_of_throwing() {
    // A propagator may probe for header names mocapi doesn't pin (e.g. b3, X-B3-TraceId). The
    // getter must degrade to "not present" (null) rather than throwing, or one unrecognized probe
    // would break every registered ObservationHandler for the whole request.
    var context = new McpRequestReceiverContext(CARRIER);

    assertThat(context.getGetter().get(CARRIER, "b3")).isNull();
  }
}
