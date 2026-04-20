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

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.tools.CallToolHandlerConfig;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import org.jwcarman.methodical.ParameterResolver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiO11yMeterIntegrationTest {

  @Test
  void tool_invocation_produces_meter_with_handler_tags() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MocapiO11yAutoConfiguration.class))
        .withUserConfiguration(MeteredObservationConfig.class)
        .run(
            context -> {
              MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
              CallToolHandlerCustomizer customizer =
                  context.getBean(CallToolHandlerCustomizer.class);

              var config = new StubToolConfig(new Tool("my-tool", null, null, null, null));
              customizer.customize(config);

              assertThat(config.interceptors).hasSize(1);
              config.interceptors.getFirst().intercept(noopInvocation());

              var timer =
                  meterRegistry
                      .find("mcp.tool")
                      .tag("mcp.handler.kind", "tool")
                      .tag("mcp.handler.name", "my-tool")
                      .timer();
              assertThat(timer).isNotNull();
              assertThat(timer.count()).isEqualTo(1L);
            });
  }

  private static MethodInvocation<JsonNode> noopInvocation() {
    return MethodInvocation.of(dummyMethod(), new Object(), null, new Object[0], () -> null);
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  @Configuration
  static class MeteredObservationConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ObservationRegistry observationRegistry(MeterRegistry meterRegistry) {
      ObservationRegistry registry = ObservationRegistry.create();
      registry
          .observationConfig()
          .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
      return registry;
    }
  }

  private static final class StubToolConfig implements CallToolHandlerConfig {
    private final Tool descriptor;
    private final List<MethodInterceptor<? super JsonNode>> interceptors = new ArrayList<>();

    StubToolConfig(Tool descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public Tool descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return dummyMethod();
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig guard(com.callibrity.mocapi.server.guards.Guard guard) {
      return this;
    }

    @Override
    public CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver) {
      return this;
    }
  }
}
