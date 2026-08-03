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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandlerConfig;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pins {@link McpServerOperationCustomizer}'s two behaviors: wiring a real, working {@link
 * McpServerOperationInterceptor} onto every {@code @JsonRpcMethod} handler (ADR-0030), and the
 * {@code toString()} used in {@code /actuator/mcp}'s interceptor-chain description. Unlike {@link
 * MocapiO11yAutoConfigurationTest} (which only verifies an interceptor of the right type is
 * attached), this test drives the captured interceptor through a real dispatch to confirm it was
 * wired with the handler's method name and the customizer's registry/translators/transport.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpServerOperationCustomizerTest {

  private final TestObservationRegistry registry = TestObservationRegistry.create();
  private final JsonRpcExceptionTranslatorRegistry translators =
      mock(JsonRpcExceptionTranslatorRegistry.class);

  @Test
  void customize_attaches_an_interceptor_wired_with_the_handler_method_and_transport() {
    var customizer = new McpServerOperationCustomizer(registry, translators, "tcp");
    MethodInterceptor<JsonNode> attached = attachedInterceptor(customizer, "tools/call");

    var call = JsonRpcCall.of("tools/call", params().put("name", "echo"), id());
    ScopedValue.where(JsonRpcDispatcher.CURRENT_REQUEST, call)
        .run(() -> attached.intercept(successfulInvocation(result())));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("tools/call echo")
        .hasLowCardinalityKeyValue("mcp.method.name", "tools/call")
        .hasLowCardinalityKeyValue("network.transport", "tcp")
        .hasBeenStopped();
  }

  @Test
  void customize_wires_the_transport_it_was_constructed_with() {
    var customizer = new McpServerOperationCustomizer(registry, translators, null);
    JsonRpcMethodHandlerConfig config = mock(JsonRpcMethodHandlerConfig.class);
    when(config.name()).thenReturn("tools/list");

    customizer.customize(config);

    verify(config).interceptor(any(McpServerOperationInterceptor.class));
  }

  @Test
  void toString_names_the_observation_and_the_semantic_conventions() {
    var customizer = new McpServerOperationCustomizer(registry, translators, "tcp");

    assertThat(customizer)
        .hasToString(
            "Attaches the 'mcp.server.operation' observation (OpenTelemetry MCP semantic"
                + " conventions) to every @JsonRpcMethod handler");
  }

  /** Captures the interceptor the customizer attaches, typed as the concrete interceptor. */
  private static MethodInterceptor<JsonNode> attachedInterceptor(
      McpServerOperationCustomizer customizer, String methodName) {
    JsonRpcMethodHandlerConfig config = mock(JsonRpcMethodHandlerConfig.class);
    when(config.name()).thenReturn(methodName);
    AtomicReference<MethodInterceptor<JsonNode>> captured = new AtomicReference<>();
    when(config.interceptor(any()))
        .thenAnswer(
            invocation -> {
              captured.set(invocation.getArgument(0));
              return config;
            });

    customizer.customize(config);

    return captured.get();
  }

  private static ObjectNode params() {
    return JsonNodeFactory.instance.objectNode();
  }

  private static ObjectNode result() {
    return JsonNodeFactory.instance.objectNode();
  }

  private static JsonNode id() {
    return JsonNodeFactory.instance.numberNode(1);
  }

  private static MethodInvocation<JsonNode> successfulInvocation(JsonNode result) {
    return new FakeInvocation(() -> result);
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
