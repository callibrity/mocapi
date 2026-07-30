/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InputSchemaValidatingInterceptorTest {

  @Test
  void toString_describes_role() {
    assertThat(new InputSchemaValidatingInterceptor(null))
        .hasToString("Validates tool arguments against the tool's input JSON schema");
  }

  /**
   * SEP-2106 regression coverage: tool input schemas are full JSON Schema 2020-12 documents.
   * Composition keywords ({@code oneOf}) and internal {@code $ref}/{@code $defs} must neither be
   * rejected at compile time nor ignored at validation time.
   */
  @Nested
  class Json_schema_2020_12_keywords {

    private static final String SCHEMA_WITH_DEFS_REF_AND_ONE_OF =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "payment": { "$ref": "#/$defs/payment" }
          },
          "required": ["payment"],
          "$defs": {
            "payment": {
              "oneOf": [
                {
                  "type": "object",
                  "properties": { "card": { "type": "string" } },
                  "required": ["card"]
                },
                {
                  "type": "object",
                  "properties": { "iban": { "type": "string" } },
                  "required": ["iban"]
                }
              ]
            }
          }
        }
        """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final InputSchemaValidatingInterceptor interceptor =
        new InputSchemaValidatingInterceptor(compile(SCHEMA_WITH_DEFS_REF_AND_ONE_OF));

    @Test
    void schema_with_defs_internal_ref_and_one_of_accepts_matching_arguments() {
      JsonNode args =
          mapper.readTree(
              """
          {"payment": {"card": "4111"}}
          """);

      Object result = interceptor.intercept(invocation(args, "ok"));

      assertThat(result).isEqualTo("ok");
    }

    @Test
    void composition_keywords_are_enforced_rather_than_stripped_or_rejected() {
      JsonNode args =
          mapper.readTree(
              """
          {"payment": {"neither": true}}
          """);
      var invocation = invocation(args, "never");

      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }

    private static Schema compile(String schemaJson) {
      return new SchemaLoader(new JsonParser(schemaJson).parse()).load();
    }
  }

  private static MethodInvocation<JsonNode> invocation(JsonNode args, Object returnValue) {
    return MethodInvocation.of(dummyMethod(), new Object(), args, new Object[0], () -> returnValue);
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }
}
