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
package com.callibrity.mocapi.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.stream.McpStreamContextScopedValueResolver;
import com.callibrity.mocapi.tools.annotation.Tool;
import com.callibrity.mocapi.tools.annotation.ToolService;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ToolServiceMcpToolProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MethodInvokerFactory invokerFactory =
      new DefaultMethodInvokerFactory(
          List.of(
              new Jackson3ParameterResolver(mapper), new McpStreamContextScopedValueResolver()));

  private ObjectNode objectSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    return schema;
  }

  private MethodSchemaGenerator createSchemaGenerator() {
    return new MethodSchemaGenerator() {
      @Override
      public ObjectNode generateInputSchema(Object targetObject, Method method) {
        return objectSchema();
      }

      @Override
      public ObjectNode generateOutputSchema(Object targetObject, Method method) {
        return objectSchema();
      }

      @Override
      public ObjectNode generateSchema(Class<?> type) {
        return objectSchema();
      }
    };
  }

  @Test
  void initializeShouldDiscoverToolServiceBeans() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBeansWithAnnotation(ToolService.class))
        .thenReturn(Map.of("sampleTools", new SampleToolService()));

    var provider = new ToolServiceMcpToolProvider(context, createSchemaGenerator(), invokerFactory);
    provider.initialize();

    assertThat(provider.getMcpTools()).hasSize(1);
    assertThat(provider.getMcpTools().getFirst().name()).contains("greet");
  }

  @Test
  void getMcpToolsShouldReturnDiscoveredTools() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBeansWithAnnotation(ToolService.class))
        .thenReturn(Map.of("sampleTools", new SampleToolService()));

    var provider = new ToolServiceMcpToolProvider(context, createSchemaGenerator(), invokerFactory);
    provider.initialize();

    var tools = provider.getMcpTools();
    assertThat(tools).hasSize(1).isUnmodifiable();
  }

  @Test
  void multipleToolServiceBeansShouldContributeTools() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBeansWithAnnotation(ToolService.class))
        .thenReturn(
            Map.of(
                "sampleTools", new SampleToolService(),
                "otherTools", new OtherToolService()));

    var provider = new ToolServiceMcpToolProvider(context, createSchemaGenerator(), invokerFactory);
    provider.initialize();

    assertThat(provider.getMcpTools()).hasSize(2);
  }

  @Test
  void beanWithNoToolMethodsShouldProduceNoTools() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBeansWithAnnotation(ToolService.class))
        .thenReturn(Map.of("empty", new EmptyToolService()));

    var provider = new ToolServiceMcpToolProvider(context, createSchemaGenerator(), invokerFactory);
    provider.initialize();

    assertThat(provider.getMcpTools()).isEmpty();
  }

  @ToolService
  static class SampleToolService {
    @Tool(description = "Greets someone")
    public GreetResult greet(String name) {
      return new GreetResult("Hello, " + name);
    }
  }

  @ToolService
  static class OtherToolService {
    @Tool(description = "Echoes input")
    public EchoResult echo(String input) {
      return new EchoResult(input);
    }
  }

  @ToolService
  static class EmptyToolService {}

  record GreetResult(String message) {}

  record EchoResult(String output) {}
}
