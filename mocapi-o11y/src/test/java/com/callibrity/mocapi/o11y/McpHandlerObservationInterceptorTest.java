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

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.handler.HandlerKind;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;

/**
 * Pins the mocapi-specific {@code mcp.handler.execution} child observation (ADR-0030): handler kind
 * + target name only. All semantic-convention attributes (gen_ai.*, mcp.*, error codes) live on the
 * outer {@code mcp.server.operation} observation — this one deliberately carries none of them.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpHandlerObservationInterceptorTest {

  private final TestObservationRegistry registry = TestObservationRegistry.create();

  @Test
  void tool_invocation_records_kind_and_target_name_only() {
    var interceptor = new McpHandlerObservationInterceptor(registry, HandlerKind.TOOL, "my-tool");

    Object result = interceptor.intercept(successfulInvocation("ok"));

    org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("my-tool")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "tool")
        // Semconv attributes belong to the outer mcp.server.operation observation, not here.
        .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.operation.name")
        .doesNotHaveLowCardinalityKeyValueWithKey("gen_ai.tool.name")
        .doesNotHaveLowCardinalityKeyValueWithKey("error.type")
        .hasBeenStopped();
  }

  @Test
  void prompt_invocation_records_prompt_kind() {
    var interceptor =
        new McpHandlerObservationInterceptor(registry, HandlerKind.PROMPT, "greeting");
    interceptor.intercept(successfulInvocation(null));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("greeting")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "prompt");
  }

  @Test
  void resource_invocation_records_resource_kind_with_uri_as_contextual_name() {
    var interceptor =
        new McpHandlerObservationInterceptor(registry, HandlerKind.RESOURCE, "mem://hello");
    interceptor.intercept(successfulInvocation(null));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("mem://hello")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "resource");
  }

  @Test
  void resource_template_invocation_records_template_kind() {
    var interceptor =
        new McpHandlerObservationInterceptor(
            registry, HandlerKind.RESOURCE_TEMPLATE, "mem://item/{id}");
    interceptor.intercept(successfulInvocation(null));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("mem://item/{id}")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "resource_template");
  }

  @Test
  void exception_path_records_the_error_and_rethrows() {
    var interceptor = new McpHandlerObservationInterceptor(registry, HandlerKind.TOOL, "my-tool");
    MethodInvocation<?> invocation = mock(MethodInvocation.class);
    when(invocation.proceed()).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(IllegalStateException.class);

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasError()
        .hasBeenStopped();
  }

  @Test
  void toString_describes_role_with_handler_kind_and_target_name() {
    var interceptor = new McpHandlerObservationInterceptor(registry, HandlerKind.TOOL, "weather");
    org.assertj.core.api.Assertions.assertThat(interceptor)
        .hasToString(
            "Records Micrometer 'mcp.handler.execution' observations"
                + " (handler execution time) for tool 'weather'");
  }

  private static MethodInvocation<?> successfulInvocation(Object result) {
    MethodInvocation<?> invocation = mock(MethodInvocation.class);
    when(invocation.proceed()).thenReturn(result);
    return invocation;
  }
}
