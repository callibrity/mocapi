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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.intercept.MethodInvocation;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpObservationInterceptorTest {

  @Test
  void tool_invocation_records_observation_with_handler_tags() {
    assertObservationRecorded("mcp.tool", "tool", "my-tool");
  }

  @Test
  void prompt_invocation_records_observation_with_handler_tags() {
    assertObservationRecorded("mcp.prompt", "prompt", "my-prompt");
  }

  @Test
  void resource_invocation_records_observation_with_handler_tags() {
    assertObservationRecorded("mcp.resource", "resource", "mem://hello");
  }

  @Test
  void resource_template_invocation_records_observation_with_handler_tags() {
    assertObservationRecorded("mcp.resource_template", "resource_template", "mem://item/{id}");
  }

  @Test
  void records_error_when_proceed_throws() {
    var registry = TestObservationRegistry.create();
    var interceptor = new McpObservationInterceptor(registry, "tool", "boom-tool");
    var invocation =
        MethodInvocation.of(
            dummyMethod(),
            new Fixtures(),
            null,
            new Object[0],
            () -> {
              throw new IllegalStateException("boom");
            });

    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(IllegalStateException.class);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo("mcp.tool")
        .that()
        .hasError()
        .hasLowCardinalityKeyValue("mcp.handler.kind", "tool")
        .hasLowCardinalityKeyValue("mcp.handler.name", "boom-tool");
  }

  private static void assertObservationRecorded(String observationName, String kind, String name) {
    var registry = TestObservationRegistry.create();
    var interceptor = new McpObservationInterceptor(registry, kind, name);

    Object result =
        interceptor.intercept(
            MethodInvocation.of(dummyMethod(), new Fixtures(), null, new Object[0], () -> "ok"));

    assertThat(result).isEqualTo("ok");
    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(observationName)
        .that()
        .hasBeenStarted()
        .hasBeenStopped()
        .hasLowCardinalityKeyValue("mcp.handler.kind", kind)
        .hasLowCardinalityKeyValue("mcp.handler.name", name);
  }

  private static Method dummyMethod() {
    try {
      return Fixtures.class.getDeclaredMethod("target");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  static class Fixtures {
    public void target() {}
  }
}
