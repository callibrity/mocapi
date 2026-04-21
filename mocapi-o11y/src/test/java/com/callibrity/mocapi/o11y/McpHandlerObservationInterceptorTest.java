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

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpHandlerObservationInterceptorTest {

  private final TestObservationRegistry registry = TestObservationRegistry.create();

  @Test
  void tool_invocation_tags_observation_with_tool_conventions_and_no_error() {
    var interceptor = new McpHandlerObservationInterceptor(registry, HandlerKind.TOOL, "my-tool");
    MethodInvocation<?> invocation = successfulInvocation("ok");

    Object result = interceptor.intercept(invocation);

    org.assertj.core.api.Assertions.assertThat(result).isEqualTo("ok");
    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("my-tool")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "tool")
        .hasLowCardinalityKeyValue("gen_ai.operation.name", "execute_tool")
        .hasLowCardinalityKeyValue("gen_ai.tool.name", "my-tool")
        .doesNotHaveLowCardinalityKeyValueWithKey("error.type")
        .hasBeenStopped();
  }

  @Test
  void prompt_invocation_tags_observation_with_prompt_conventions() {
    var interceptor =
        new McpHandlerObservationInterceptor(registry, HandlerKind.PROMPT, "greeting");
    interceptor.intercept(successfulInvocation(null));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("greeting")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "prompt")
        .hasLowCardinalityKeyValue("gen_ai.prompt.name", "greeting");
  }

  @Test
  void resource_invocation_tags_observation_with_resource_uri() {
    var interceptor =
        new McpHandlerObservationInterceptor(registry, HandlerKind.RESOURCE, "mem://hello");
    interceptor.intercept(successfulInvocation(null));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("mem://hello")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "resource")
        .hasHighCardinalityKeyValue("mcp.resource.uri", "mem://hello");
  }

  @Test
  void resource_template_invocation_tags_observation_with_resource_uri() {
    var interceptor =
        new McpHandlerObservationInterceptor(
            registry, HandlerKind.RESOURCE_TEMPLATE, "mem://item/{id}");
    interceptor.intercept(successfulInvocation(null));

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasContextualNameEqualTo("mem://item/{id}")
        .hasLowCardinalityKeyValue("mcp.handler.kind", "resource_template")
        .hasHighCardinalityKeyValue("mcp.resource.uri", "mem://item/{id}");
  }

  @Test
  void exception_path_records_error_type_and_rethrows() {
    var interceptor = new McpHandlerObservationInterceptor(registry, HandlerKind.TOOL, "my-tool");
    MethodInvocation<?> invocation = mock(MethodInvocation.class);
    when(invocation.proceed()).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(IllegalStateException.class);

    assertThat(registry)
        .hasObservationWithNameEqualTo(McpHandlerObservationInterceptor.OBSERVATION_NAME)
        .that()
        .hasLowCardinalityKeyValue("error.type", "IllegalStateException")
        .hasBeenStopped();
  }

  private static MethodInvocation<?> successfulInvocation(Object result) {
    MethodInvocation<?> invocation = mock(MethodInvocation.class);
    when(invocation.proceed()).thenReturn(result);
    return invocation;
  }
}
