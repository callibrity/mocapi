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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.HelloTool;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolCallDispatchCustomizerTest {

  private static final String HELLO_TOOL_NAME = "hello-tool.say-hello";

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);
  private final MrtrElicitationEngine elicitationEngine =
      new MrtrElicitationEngine(
          RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, new ObjectMapper()),
          new ObjectMapper());

  private List<CallToolHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpTool.class).stream()
        .map(
            m ->
                CallToolHandlers.build(
                    target,
                    m,
                    new CallToolHandlers.BuildParams(
                        generator, mapper, List.of(), List.of(), s -> s, false)))
        .toList();
  }

  private McpToolsService serviceWith(List<ToolCallDispatchCustomizer> customizers) {
    return new McpToolsService(
        createHandlers(new HelloTool()),
        mapper,
        elicitationEngine,
        McpToolsService.DEFAULT_PAGE_SIZE,
        CacheSettings.defaults(),
        customizers);
  }

  @Test
  void claiming_customizer_short_circuits_and_skips_handler() {
    ToolCallDispatchCustomizer claim =
        (handler, params) ->
            params.name().equals(HELLO_TOOL_NAME) ? Optional.of("CLAIMED") : Optional.empty();
    McpToolsService svc = serviceWith(List.of(claim));

    Object response =
        svc.callTool(
            new CallToolRequestParams(
                HELLO_TOOL_NAME, mapper.createObjectNode().put("name", "World"), null, null, null));

    // "CLAIMED" is a bare String, never the CallToolResult the default invokeTool path always
    // produces — proof the handler's method body never ran.
    assertThat(response).isEqualTo("CLAIMED");
  }

  @Test
  void unclaimed_call_falls_through_to_the_default_path() {
    ToolCallDispatchCustomizer neverClaims = (handler, params) -> Optional.empty();
    McpToolsService svc = serviceWith(List.of(neverClaims));

    Object response =
        svc.callTool(
            new CallToolRequestParams(
                HELLO_TOOL_NAME, mapper.createObjectNode().put("name", "World"), null, null, null));

    assertThat(response).isInstanceOf(CallToolResult.class);
    CallToolResult result = (CallToolResult) response;
    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent().get("message").stringValue()).isEqualTo("Hello, World!");
  }

  @Test
  void all_empty_customizers_behave_identically_to_no_customizers() {
    McpToolsService withEmptyCustomizer =
        serviceWith(List.of((handler, params) -> Optional.empty()));
    McpToolsService withoutCustomizers = serviceWith(List.of());

    var params =
        new CallToolRequestParams(
            HELLO_TOOL_NAME, mapper.createObjectNode().put("name", "World"), null, null, null);

    CallToolResult resultWithCustomizer = (CallToolResult) withEmptyCustomizer.callTool(params);
    CallToolResult resultWithoutCustomizers = (CallToolResult) withoutCustomizers.callTool(params);

    assertThat(resultWithCustomizer.structuredContent())
        .isEqualTo(resultWithoutCustomizers.structuredContent());
    assertThat(resultWithCustomizer.isError()).isEqualTo(resultWithoutCustomizers.isError());
  }
}
