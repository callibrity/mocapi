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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.mocapi.stream.McpStreamContextScopedValueResolver;
import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import tools.jackson.databind.ObjectMapper;

class AnnotationMcpToolValidationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MethodInvokerFactory invokerFactory =
      new DefaultMethodInvokerFactory(
          List.of(
              new Jackson3ParameterResolver(mapper), new McpStreamContextScopedValueResolver()));
  private final AnnotationMcpToolProviderFactory factory =
      new DefaultAnnotationMcpToolProviderFactory(
          new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7), invokerFactory);

  @Test
  void streamingToolWithNonVoidReturnShouldBeRejected() {
    assertThatThrownBy(() -> factory.create(new IllegalStreamingTool()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("doWork")
        .hasMessageContaining("must return void")
        .hasMessageContaining("ctx.sendResult(R)");
  }

  @Test
  void streamingToolWithVoidReturnShouldBeAccepted() {
    var tools = factory.create(new ValidStreamingTool());
    var mcpTools = tools.getMcpTools();
    org.assertj.core.api.Assertions.assertThat(mcpTools).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(mcpTools.getFirst().isStreamable()).isTrue();
  }

  @ToolService
  static class IllegalStreamingTool {
    @ToolMethod(name = "illegal", description = "Illegally returns a value from a streaming tool")
    public Map<String, Object> doWork(String input, McpStreamContext<Map<String, Object>> ctx) {
      return Map.of("result", input);
    }
  }

  @ToolService
  static class ValidStreamingTool {
    @ToolMethod(name = "valid", description = "Correctly returns void from a streaming tool")
    public void doWork(String input, McpStreamContext<Map<String, Object>> ctx) {
      ctx.sendResult(Map.of("result", input));
    }
  }
}
