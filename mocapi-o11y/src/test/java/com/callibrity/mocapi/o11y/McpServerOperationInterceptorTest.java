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

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pins the OpenTelemetry MCP semantic-convention shape of the {@code mcp.server.operation}
 * observation (ADR-0030): observation/metric name, {@code {method} {target}} span naming, the
 * required and recommended attributes, JSON-RPC error-code {@code error.type} semantics, and the
 * {@code tool_error} case for a {@code CallToolResult} with {@code isError=true}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpServerOperationInterceptorTest {

  private final TestObservationRegistry registry = TestObservationRegistry.create();
  private final JsonRpcExceptionTranslatorRegistry translators =
      mock(JsonRpcExceptionTranslatorRegistry.class);

  @Nested
  class Span_naming_and_target_attributes {

    @Test
    void tools_call_span_is_named_method_space_tool_name() {
      var interceptor = interceptor("tools/call", "tcp");
      var call = call("tools/call", params().put("name", "echo"));

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("tools/call echo")
          .hasLowCardinalityKeyValue("mcp.method.name", "tools/call")
          .hasLowCardinalityKeyValue("gen_ai.operation.name", "execute_tool")
          .hasLowCardinalityKeyValue("gen_ai.tool.name", "echo")
          .hasBeenStopped();
    }

    @Test
    void prompts_get_span_is_named_method_space_prompt_name() {
      var interceptor = interceptor("prompts/get", "tcp");
      var call = call("prompts/get", params().put("name", "greeting"));

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("prompts/get greeting")
          .hasLowCardinalityKeyValue("gen_ai.prompt.name", "greeting");
    }

    @Test
    void resources_read_span_keeps_the_bare_method_name_and_carries_the_uri_as_high_cardinality() {
      // Per the conventions, mcp.resource.uri must NOT become a span-name target by default —
      // it would produce high-cardinality span names.
      var interceptor = interceptor("resources/read", "tcp");
      var call = call("resources/read", params().put("uri", "mem://hello"));

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("resources/read")
          .hasHighCardinalityKeyValue("mcp.resource.uri", "mem://hello");
    }

    @Test
    void targetless_method_span_is_the_bare_method_name_with_no_gen_ai_attributes() {
      var interceptor = interceptor("tools/list", "tcp");
      var call = call("tools/list", params());

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("tools/list")
          .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.operation.name")
          .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.tool.name");
    }
  }

  @Nested
  class Envelope_and_transport_attributes {

    @Test
    void emits_the_jsonrpc_mcp_and_network_attribute_set() {
      var interceptor = interceptor("tools/list", "tcp");
      var call = call("tools/list", params());
      var exchange = new McpExchange("2026-07-28", null, null);

      ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, call)
          .where(McpExchange.CURRENT, exchange)
          .run(() -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasLowCardinalityKeyValue("rpc.system.name", "jsonrpc")
          .hasLowCardinalityKeyValue("jsonrpc.protocol.version", "2.0")
          .hasLowCardinalityKeyValue("mcp.protocol.version", "2026-07-28")
          .hasLowCardinalityKeyValue("network.transport", "tcp")
          .hasLowCardinalityKeyValue("network.protocol.name", "http")
          .hasHighCardinalityKeyValue("jsonrpc.request.id", "1");
    }

    @Test
    void stdio_transport_is_pipe_with_no_http_protocol_name() {
      var interceptor = interceptor("tools/list", "pipe");
      var call = call("tools/list", params());

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasLowCardinalityKeyValue("network.transport", "pipe")
          .doesNotHaveLowCardinalityKeyValueWithKey("network.protocol.name");
    }

    @Test
    void unknown_transport_omits_the_network_attributes() {
      var interceptor = interceptor("tools/list", null);
      var call = call("tools/list", params());

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveLowCardinalityKeyValueWithKey("network.transport");
    }

    @Test
    void works_without_bound_request_or_exchange_scoped_values() {
      var interceptor = interceptor("tools/list", "tcp");

      interceptor.intercept(successfulInvocation(result()));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("tools/list")
          .doesNotHaveLowCardinalityKeyValueWithKey("mcp.protocol.version")
          .doesNotHaveHighCardinalityKeyValueWithKey("jsonrpc.request.id")
          .hasBeenStopped();
    }

    @Test
    void bound_exchange_with_a_null_protocol_version_omits_the_attribute() {
      // Guards the isBound() && protocolVersion() != null branch: an exchange can be bound
      // (the _meta envelope was present) while still carrying no protocol version.
      var interceptor = interceptor("tools/list", "tcp");
      var call = call("tools/list", params());
      var exchange = new McpExchange(null, null, null);

      ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, call)
          .where(McpExchange.CURRENT, exchange)
          .run(() -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveLowCardinalityKeyValueWithKey("mcp.protocol.version");
    }

    @Test
    void notification_requests_carry_no_jsonrpc_request_id() {
      // Guards the `instanceof JsonRpcCall call` branch: a bound request that is a
      // JsonRpcNotification (no id) must not produce a jsonrpc.request.id attribute.
      var interceptor = interceptor("tools/list", "tcp");
      var notification = JsonRpcNotification.of("tools/list", params());

      ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, notification)
          .run(() -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveHighCardinalityKeyValueWithKey("jsonrpc.request.id");
    }
  }

  @Nested
  class Absent_and_non_conforming_target_fields {

    @Test
    void tools_call_with_null_params_has_no_tool_name() {
      // Guards stringField's params == null branch: a bound call whose params is null (not an
      // empty object) must not blow up and must omit the target-bearing attributes.
      var interceptor = interceptor("tools/call", "tcp");
      var call = JsonRpcCall.of("tools/call", null, JsonNodeFactory.instance.numberNode(1));

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("tools/call")
          .hasLowCardinalityKeyValue("gen_ai.operation.name", "execute_tool")
          .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.tool.name");
    }

    @Test
    void tools_call_with_non_object_params_has_no_tool_name() {
      // Guards stringField's !params.isObject() branch.
      var interceptor = interceptor("tools/call", "tcp");
      var call =
          JsonRpcCall.of(
              "tools/call",
              JsonNodeFactory.instance.textNode("not-an-object"),
              JsonNodeFactory.instance.numberNode(1));

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.tool.name");
    }

    @Test
    void prompts_get_without_a_name_field_omits_the_prompt_name_attribute() {
      var interceptor = interceptor("prompts/get", "tcp");
      var call = call("prompts/get", params());

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasContextualNameEqualTo("prompts/get")
          .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.prompt.name");
    }

    @Test
    void resources_read_without_a_uri_field_omits_the_resource_uri_attribute() {
      var interceptor = interceptor("resources/read", "tcp");
      var call = call("resources/read", params());

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveHighCardinalityKeyValueWithKey("mcp.resource.uri");
    }

    @Test
    void resources_read_with_a_non_string_uri_field_omits_the_resource_uri_attribute() {
      // Guards stringField's value.isString() branch: a uri field that parses but isn't a string.
      var interceptor = interceptor("resources/read", "tcp");
      var call = call("resources/read", params().put("uri", 42));

      inScope(call, () -> interceptor.intercept(successfulInvocation(result())));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveHighCardinalityKeyValueWithKey("mcp.resource.uri");
    }
  }

  @Nested
  class Error_semantics {

    @Test
    void thrown_exception_records_the_json_rpc_error_code_not_the_class_name() {
      var boom = new IllegalStateException("boom");
      when(translators.translate(boom)).thenReturn(new JsonRpcErrorDetail(-32603, "boom"));
      var interceptor = interceptor("tools/call", "tcp");
      var invocation = invocationThrowing(boom);

      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(IllegalStateException.class);

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasLowCardinalityKeyValue("error.type", "-32603")
          .hasLowCardinalityKeyValue("rpc.response.status_code", "-32603")
          .hasBeenStopped();
    }

    @Test
    void tools_call_result_with_isError_true_records_tool_error() {
      // The conventions call this case out specifically: a CallToolResult with isError=true is a
      // normal JSON-RPC success on the wire, so error.type is the only queryable failure signal.
      var interceptor = interceptor("tools/call", "tcp");
      var call = call("tools/call", params().put("name", "echo"));

      inScope(
          call, () -> interceptor.intercept(successfulInvocation(result().put("isError", true))));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .hasLowCardinalityKeyValue("error.type", "tool_error")
          .doesNotHaveLowCardinalityKeyValueWithKey("rpc.response.status_code")
          .hasBeenStopped();
    }

    @Test
    void successful_tools_call_has_no_error_type() {
      var interceptor = interceptor("tools/call", "tcp");
      var call = call("tools/call", params().put("name", "echo"));

      inScope(
          call, () -> interceptor.intercept(successfulInvocation(result().put("isError", false))));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveLowCardinalityKeyValueWithKey("error.type");
    }

    @Test
    void tools_call_returning_a_null_result_is_not_treated_as_tool_error() {
      // Guards isToolError's result != null branch: a successful invocation can still return a
      // null JsonNode (e.g. a handler that legitimately produces no result).
      var interceptor = interceptor("tools/call", "tcp");
      var call = call("tools/call", params().put("name", "echo"));

      inScope(call, () -> interceptor.intercept(successfulInvocation(null)));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveLowCardinalityKeyValueWithKey("error.type")
          .hasBeenStopped();
    }

    @Test
    void isError_on_a_non_tools_call_method_is_ignored() {
      var interceptor = interceptor("prompts/get", "tcp");
      var call = call("prompts/get", params().put("name", "greeting"));

      inScope(
          call, () -> interceptor.intercept(successfulInvocation(result().put("isError", true))));

      assertThat(registry)
          .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
          .that()
          .doesNotHaveLowCardinalityKeyValueWithKey("error.type");
    }
  }

  @Test
  void toString_describes_the_observation_name_and_the_bound_method() {
    var interceptor = interceptor("tools/call", "tcp");

    org.assertj.core.api.Assertions.assertThat(interceptor)
        .hasToString(
            "Records Micrometer 'mcp.server.operation' observations (OpenTelemetry MCP semantic"
                + " conventions) for method 'tools/call'");
  }

  private McpServerOperationInterceptor interceptor(String method, String transport) {
    return new McpServerOperationInterceptor(registry, translators, method, transport);
  }

  private static void inScope(JsonRpcCall call, Runnable work) {
    ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, call).run(work);
  }

  private static JsonRpcCall call(String method, ObjectNode params) {
    return JsonRpcCall.of(method, params, JsonNodeFactory.instance.numberNode(1));
  }

  private static ObjectNode params() {
    return JsonNodeFactory.instance.objectNode();
  }

  private static ObjectNode result() {
    return JsonNodeFactory.instance.objectNode();
  }

  private static MethodInvocation<JsonNode> successfulInvocation(JsonNode result) {
    return new FakeInvocation(() -> result);
  }

  private static MethodInvocation<JsonNode> invocationThrowing(RuntimeException e) {
    return new FakeInvocation(
        () -> {
          throw e;
        });
  }

  /** Plain-Java fake — dodges Mockito's unchecked-generic warning on the interface mock. */
  private record FakeInvocation(Supplier<Object> body) implements MethodInvocation<JsonNode> {
    @Override
    public Method method() {
      return null;
    }

    @Override
    public Object target() {
      return null;
    }

    @Override
    public JsonNode argument() {
      return null;
    }

    @Override
    public Object[] resolvedParameters() {
      return new Object[0];
    }

    @Override
    public Object proceed() {
      return body.get();
    }
  }
}
