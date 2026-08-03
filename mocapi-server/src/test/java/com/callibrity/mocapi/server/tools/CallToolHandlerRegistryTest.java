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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CallToolHandlerRegistryTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  static class Fixture {
    @McpTool(description = "says hi")
    public String hello() {
      return "hi";
    }
  }

  private List<CallToolHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpTool.class).stream()
        .map(
            m ->
                CallToolHandlers.build(
                    target,
                    m,
                    new CallToolHandlers.BuildParams(generator, mapper, List.of(), s -> s, false)))
        .toList();
  }

  @Test
  void handlers_returns_the_registered_list_in_order() {
    List<CallToolHandler> handlers = createHandlers(new Fixture());
    var registry = new CallToolHandlerRegistry(handlers);

    assertThat(registry.handlers()).containsExactlyElementsOf(handlers);
  }

  @Test
  void find_by_name_returns_present_for_a_registered_handler() {
    String name = createHandlers(new Fixture()).getFirst().name();
    var registry = new CallToolHandlerRegistry(createHandlers(new Fixture()));

    assertThat(registry.findByName(name)).isPresent();
  }

  @Test
  void find_by_name_returns_empty_for_an_unknown_name() {
    var registry = new CallToolHandlerRegistry(createHandlers(new Fixture()));

    assertThat(registry.findByName("nope")).isEmpty();
  }

  @Test
  void lookup_returns_the_handler_for_a_registered_name() {
    String name = createHandlers(new Fixture()).getFirst().name();
    var registry = new CallToolHandlerRegistry(createHandlers(new Fixture()));

    assertThat(registry.lookup(name).name()).isEqualTo(name);
  }

  @Test
  void lookup_throws_invalid_params_for_an_unknown_name() {
    var registry = new CallToolHandlerRegistry(createHandlers(new Fixture()));

    assertThatThrownBy(() -> registry.lookup("nope"))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
        .hasMessageContaining("nope");
  }

  @Test
  void constructor_rejects_a_null_handler_list() {
    assertThatThrownBy(() -> new CallToolHandlerRegistry(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("handlers");
  }
}
