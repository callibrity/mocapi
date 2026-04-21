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
package com.callibrity.mocapi.o11y;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpObservationFilterTest {

  private final McpObservationFilter filter = new McpObservationFilter();

  @Test
  void passes_unrelated_observation_context_through_unchanged() {
    Observation.Context context = new Observation.Context();
    context.setName("http.server");

    Observation.Context result = filter.map(context);

    assertThat(result).isSameAs(context);
    assertThat(result.getLowCardinalityKeyValue("mcp.method.name")).isNull();
  }

  @Test
  void adds_mcp_method_name_from_bound_json_rpc_call() {
    JsonRpcRequest call =
        new JsonRpcCall("2.0", "tools/call", null, JsonNodeFactory.instance.numberNode(1));
    Observation.Context context = jsonRpcContext();

    ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, call).run(() -> filter.map(context));

    assertThat(context.getLowCardinalityKeyValue("mcp.method.name").getValue())
        .isEqualTo("tools/call");
  }

  @Test
  void adds_mcp_method_name_from_bound_json_rpc_notification() {
    JsonRpcRequest notification = new JsonRpcNotification("2.0", "notifications/initialized", null);
    Observation.Context context = jsonRpcContext();

    ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, notification)
        .run(() -> filter.map(context));

    assertThat(context.getLowCardinalityKeyValue("mcp.method.name").getValue())
        .isEqualTo("notifications/initialized");
  }

  @Test
  void adds_mcp_session_id_and_protocol_version_when_mcp_session_is_bound() {
    Observation.Context context = jsonRpcContext();
    McpSession session =
        new McpSession(
            "session-42",
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("client", "Client", "1.0"));

    ScopedValue.where(McpSession.CURRENT, session).run(() -> filter.map(context));

    assertThat(context.getLowCardinalityKeyValue("mcp.session.id").getValue())
        .isEqualTo("session-42");
    assertThat(context.getLowCardinalityKeyValue("mcp.protocol.version").getValue())
        .isEqualTo("2025-11-25");
  }

  @Test
  void omits_protocol_version_when_session_protocol_version_is_null() {
    Observation.Context context = jsonRpcContext();
    McpSession session =
        new McpSession(
            "session-42",
            null,
            new ClientCapabilities(null, null, null),
            new Implementation("client", "Client", "1.0"));

    ScopedValue.where(McpSession.CURRENT, session).run(() -> filter.map(context));

    assertThat(context.getLowCardinalityKeyValue("mcp.session.id").getValue())
        .isEqualTo("session-42");
    assertThat(context.getLowCardinalityKeyValue("mcp.protocol.version")).isNull();
  }

  @Test
  void returns_context_without_adding_any_attrs_when_no_scoped_values_bound() {
    Observation.Context context = jsonRpcContext();

    Observation.Context result = filter.map(context);

    assertThat(result).isSameAs(context);
    assertThat(context.getLowCardinalityKeyValue("mcp.method.name")).isNull();
    assertThat(context.getLowCardinalityKeyValue("mcp.session.id")).isNull();
  }

  private static Observation.Context jsonRpcContext() {
    Observation.Context context = new Observation.Context();
    context.setName("jsonrpc.server");
    return context;
  }
}
