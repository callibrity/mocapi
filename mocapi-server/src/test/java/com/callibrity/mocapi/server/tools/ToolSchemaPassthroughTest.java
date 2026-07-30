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

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvoker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SEP-2106 regression: tool schemas are full JSON Schema 2020-12 documents. A {@code Tool}
 * descriptor whose {@code inputSchema} carries composition keywords and internal {@code
 * $ref}/{@code $defs} must round-trip through {@code tools/list} byte-identical — mocapi neither
 * rejects, rewrites, nor dereferences user-supplied schema content anywhere on the list path.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolSchemaPassthroughTest {

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

  @Test
  void defs_bearing_input_schema_round_trips_through_tools_list_untouched() {
    ObjectNode schema = (ObjectNode) mapper.readTree(SCHEMA_WITH_DEFS_REF_AND_ONE_OF);
    ObjectNode pristineCopy = schema.deepCopy();

    var service =
        new McpToolsService(List.of(handlerWithInputSchema(schema)), mapper, elicitationEngine());

    JsonNode listed = service.listTools(null).tools().getFirst().inputSchema();

    assertThat(listed).isEqualTo(pristineCopy);
    String serialized = mapper.writeValueAsString(listed);
    assertThat(serialized)
        .contains("$defs")
        .contains("$ref")
        .contains("oneOf")
        .isEqualTo(mapper.writeValueAsString(pristineCopy));
  }

  /** Zero-arg target so the invoker builds without parameter resolvers; never invoked here. */
  public static class DefsTool {
    public String go() {
      return "ok";
    }
  }

  /**
   * Builds a registrable handler whose descriptor carries the supplied raw input schema. The
   * invoker is real but never invoked — this test exercises only the {@code tools/list} path.
   */
  private CallToolHandler handlerWithInputSchema(ObjectNode inputSchema) {
    var bean = new DefsTool();
    Method method;
    try {
      method = DefsTool.class.getMethod("go");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
    Tool descriptor = new Tool("defs-tool", "Defs Tool", "Uses $defs", inputSchema, null);
    MethodInvoker<JsonNode> invoker = MethodInvoker.builder(method, bean, JsonNode.class).build();
    return new CallToolHandler(
        descriptor, method, bean, invoker, List.of(), TextContentResultMapper.INSTANCE);
  }

  private MrtrElicitationEngine elicitationEngine() {
    return new MrtrElicitationEngine(
        RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, mapper), mapper);
  }
}
