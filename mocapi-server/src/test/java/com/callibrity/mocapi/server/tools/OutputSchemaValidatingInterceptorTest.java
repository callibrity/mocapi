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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutputSchemaValidatingInterceptorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final Schema schema =
      compile(
          """
          {
            "type": "object",
            "properties": {
              "name": { "type": "string" },
              "age": { "type": "integer" }
            },
            "required": ["name", "age"],
            "additionalProperties": false
          }
          """);
  private final OutputSchemaValidatingInterceptor interceptor =
      new OutputSchemaValidatingInterceptor(schema, mapper);

  @Test
  void toString_describes_role() {
    assertThat(interceptor)
        .hasToString("Validates tool return value against the tool's output JSON schema");
  }

  @Nested
  class When_tool_returns_a_domain_object {

    @Test
    void passes_through_when_serialized_value_matches_schema() {
      Object out = interceptor.intercept(invocationReturning(new Person("Ada", 36)));
      assertThat(out).isInstanceOf(Person.class);
    }

    @Test
    void throws_internal_error_when_required_field_is_missing() {
      MethodInvocation<JsonNode> invocation = invocationReturning(new OnlyName("Ada"));
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcProtocol.INTERNAL_ERROR));
    }

    @Test
    void throws_internal_error_when_field_type_is_wrong() {
      MethodInvocation<JsonNode> invocation = invocationReturning(new WrongTypes("Ada", "thirty"));
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(JsonRpcException.class);
    }
  }

  @Nested
  class When_tool_returns_a_CallToolResult {

    @Test
    void validates_structured_content_when_present() {
      ObjectNode structured = mapper.createObjectNode().put("name", "Ada").put("age", 36);
      CallToolResult result =
          new CallToolResult(List.of(new TextContent("ok", null)), null, structured);

      Object out = interceptor.intercept(invocationReturning(result));

      assertThat(out).isSameAs(result);
    }

    @Test
    void throws_when_structured_content_violates_schema() {
      ObjectNode bad = mapper.createObjectNode().put("name", "Ada"); // missing required "age"
      CallToolResult result = new CallToolResult(List.of(new TextContent("ok", null)), null, bad);
      MethodInvocation<JsonNode> invocation = invocationReturning(result);

      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcProtocol.INTERNAL_ERROR));
    }

    @Test
    void skips_validation_when_structured_content_is_null() {
      CallToolResult textOnly =
          new CallToolResult(List.of(new TextContent("just text", null)), null, null);

      Object out = interceptor.intercept(invocationReturning(textOnly));

      assertThat(out).isSameAs(textOnly);
    }
  }

  @Nested
  class When_tool_returns_a_JsonNode {

    @Test
    void validates_json_node_directly() {
      JsonNode node = mapper.createObjectNode().put("name", "Ada").put("age", 36);

      Object out = interceptor.intercept(invocationReturning(node));

      assertThat(out).isSameAs(node);
    }

    @Test
    void throws_when_json_node_violates_schema() {
      JsonNode bad = mapper.createObjectNode().put("name", "Ada");
      MethodInvocation<JsonNode> invocation = invocationReturning(bad);

      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(JsonRpcException.class);
    }
  }

  @Nested
  class When_tool_returns_null {

    @Test
    void skips_validation() {
      Object out = interceptor.intercept(invocationReturning(null));
      assertThat(out).isNull();
    }
  }

  private MethodInvocation<JsonNode> invocationReturning(Object value) {
    Supplier<Object> continuation = () -> value;
    return MethodInvocation.of(dummyMethod(), this, null, new Object[0], continuation);
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  private static Schema compile(String json) {
    return new SchemaLoader(new JsonParser(json).parse()).load();
  }

  record Person(String name, int age) {}

  record OnlyName(String name) {}

  record WrongTypes(String name, String age) {}
}
