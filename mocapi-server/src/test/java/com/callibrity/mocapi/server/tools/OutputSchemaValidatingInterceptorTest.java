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

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link OutputSchemaValidatingInterceptor}. Under the post-strictness-pass
 * contract, the validator is only installed when a handler is paired with {@link
 * StructuredResultMapper} — so the only values it ever sees are {@code null} (tolerated) or a
 * POJO/record whose Jackson serialization is a JSON object.
 */
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
      Person value = new Person("Ada", 36);
      Object out = interceptor.intercept(invocationReturning(value));
      assertThat(out).isSameAs(value);
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

    @Test
    void throws_internal_error_when_extra_property_is_present() {
      MethodInvocation<JsonNode> invocation =
          invocationReturning(new WithExtra("Ada", 36, "extra"));
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcProtocol.INTERNAL_ERROR));
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

  record WithExtra(String name, int age, String extra) {}
}
