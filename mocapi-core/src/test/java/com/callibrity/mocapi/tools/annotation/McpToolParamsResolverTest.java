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
package com.callibrity.mocapi.tools.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

class McpToolParamsResolverTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final McpToolParamsResolver resolver = new McpToolParamsResolver(mapper);

  public record GreetRequest(String name, int volume) {}

  void annotatedMethod(@McpToolParams GreetRequest request) {}

  void unannotatedMethod(String name) {}

  @Test
  void supportsShouldReturnTrueForAnnotatedParameter() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("annotatedMethod", GreetRequest.class).getParameters()[0];
    ParameterInfo info = mock(ParameterInfo.class);
    doReturn(param).when(info).parameter();
    assertThat(resolver.supports(info)).isTrue();
  }

  @Test
  void supportsShouldReturnFalseForUnannotatedParameter() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("unannotatedMethod", String.class).getParameters()[0];
    ParameterInfo info = mock(ParameterInfo.class);
    doReturn(param).when(info).parameter();
    assertThat(resolver.supports(info)).isFalse();
  }

  @Test
  void resolveShouldDeserializeValidJsonIntoRecord() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("annotatedMethod", GreetRequest.class).getParameters()[0];
    ParameterInfo info = mock(ParameterInfo.class);
    doReturn(param).when(info).parameter();
    doReturn(GreetRequest.class).when(info).resolvedType();

    var arguments = mapper.createObjectNode().put("name", "Alice").put("volume", 3);
    Object result = resolver.resolve(info, arguments);

    assertThat(result).isInstanceOf(GreetRequest.class);
    GreetRequest request = (GreetRequest) result;
    assertThat(request.name()).isEqualTo("Alice");
    assertThat(request.volume()).isEqualTo(3);
  }

  @Test
  void resolveShouldReturnNullForNullArguments() throws Exception {
    ParameterInfo info = mock(ParameterInfo.class);
    assertThat(resolver.resolve(info, null)).isNull();
  }

  @Test
  void resolveShouldReturnNullForNullNode() throws Exception {
    ParameterInfo info = mock(ParameterInfo.class);
    assertThat(resolver.resolve(info, NullNode.instance)).isNull();
  }

  @Test
  void resolveShouldThrowParameterResolutionExceptionForInvalidJson() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("annotatedMethod", GreetRequest.class).getParameters()[0];
    ParameterInfo info = mock(ParameterInfo.class);
    doReturn(param).when(info).parameter();
    doReturn(GreetRequest.class).when(info).resolvedType();
    doReturn("request").when(info).name();

    var arguments = mapper.createObjectNode().put("name", "Alice").put("volume", "not-a-number");

    assertThatThrownBy(() -> resolver.resolve(info, arguments))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("@McpToolParams")
        .hasMessageContaining("request");
  }
}
