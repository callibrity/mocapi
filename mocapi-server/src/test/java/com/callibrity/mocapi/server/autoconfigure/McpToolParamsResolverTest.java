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
package com.callibrity.mocapi.server.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.api.tools.McpToolParams;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolParamsResolverTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final McpToolParamsResolver resolver = new McpToolParamsResolver(mapper);

  record TestParams(String name, int age) {}

  void methodWithAnnotated(@McpToolParams TestParams params) {
    // Reflective target — parameter metadata used by supports() and resolve() tests
  }

  void methodWithoutAnnotation(String plain) {
    // Reflective target — parameter without @McpToolParams for negative test
  }

  @Test
  void supports_parameter_with_mcp_tool_params_annotation() throws Exception {
    var param =
        getClass().getDeclaredMethod("methodWithAnnotated", TestParams.class).getParameters()[0];
    var info = paramInfo(param, TestParams.class);
    assertThat(resolver.supports(info)).isTrue();
  }

  @Test
  void does_not_support_parameter_without_annotation() throws Exception {
    var param =
        getClass().getDeclaredMethod("methodWithoutAnnotation", String.class).getParameters()[0];
    var info = paramInfo(param, String.class);
    assertThat(resolver.supports(info)).isFalse();
  }

  @Test
  void resolves_json_to_record() throws Exception {
    var param =
        getClass().getDeclaredMethod("methodWithAnnotated", TestParams.class).getParameters()[0];
    var info = paramInfo(param, TestParams.class);
    var json = mapper.createObjectNode().put("name", "Alice").put("age", 30);

    var result = (TestParams) resolver.resolve(info, json);

    assertThat(result.name()).isEqualTo("Alice");
    assertThat(result.age()).isEqualTo(30);
  }

  @Test
  void returns_null_for_null_arguments() throws Exception {
    var param =
        getClass().getDeclaredMethod("methodWithAnnotated", TestParams.class).getParameters()[0];
    var info = paramInfo(param, TestParams.class);

    assertThat(resolver.resolve(info, null)).isNull();
  }

  @Test
  void returns_null_for_json_null() throws Exception {
    var param =
        getClass().getDeclaredMethod("methodWithAnnotated", TestParams.class).getParameters()[0];
    var info = paramInfo(param, TestParams.class);

    assertThat(resolver.resolve(info, JsonNodeFactory.instance.nullNode())).isNull();
  }

  @Test
  void throws_parameter_resolution_exception_for_invalid_json() throws Exception {
    var param =
        getClass().getDeclaredMethod("methodWithAnnotated", TestParams.class).getParameters()[0];
    var info = paramInfo(param, TestParams.class);
    var invalidJson = mapper.createObjectNode().put("name", "Alice").put("age", "not-a-number");

    assertThatThrownBy(() -> resolver.resolve(info, invalidJson))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("@McpToolParams");
  }

  private static ParameterInfo paramInfo(Parameter param, Class<?> resolvedType) {
    var info = mock(ParameterInfo.class);
    when(info.parameter()).thenReturn(param);
    doReturn(resolvedType).when(info).resolvedType();
    when(info.name()).thenReturn(param.getName());
    return info;
  }
}
