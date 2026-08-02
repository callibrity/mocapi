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
package com.callibrity.mocapi.server.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.mocapi.server.prompts.GetPromptHandler;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.CallToolHandlers;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.HelloTool;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpDispatchInterceptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String HELLO_TOOL_NAME = "hello-tool.say-hello";

  private static MrtrElicitationEngine engine() {
    return new MrtrElicitationEngine(
        RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, MAPPER), MAPPER);
  }

  // --- DispatchChains unit-level behavior -----------------------------------------------------

  @Order(1)
  private record RecordingInterceptor(String name, List<String> trace)
      implements McpDispatchInterceptor<String, String> {
    @Override
    public Object intercept(String handler, String params, Supplier<Object> proceed) {
      trace.add(name + "-enter");
      Object result = proceed.get();
      trace.add(name + "-exit");
      return result;
    }
  }

  @Order(2)
  private record RecordingInterceptorLow(String name, List<String> trace)
      implements McpDispatchInterceptor<String, String> {
    @Override
    public Object intercept(String handler, String params, Supplier<Object> proceed) {
      trace.add(name + "-enter");
      Object result = proceed.get();
      trace.add(name + "-exit");
      return result;
    }
  }

  @Test
  void order_controls_nesting_lower_values_run_outermost() {
    List<String> trace = new ArrayList<>();
    McpDispatchInterceptor<String, String> outer = new RecordingInterceptor("outer", trace);
    McpDispatchInterceptor<String, String> inner = new RecordingInterceptorLow("inner", trace);

    List<McpDispatchInterceptor<String, String>> sorted =
        DispatchChains.sort(List.of(inner, outer));
    Object result = DispatchChains.run(sorted, "h", "p", () -> "terminal");

    assertThat(result).isEqualTo("terminal");
    assertThat(trace).containsExactly("outer-enter", "inner-enter", "inner-exit", "outer-exit");
  }

  @Test
  void decorating_interceptor_composes_with_a_claiming_one() {
    McpDispatchInterceptor<String, String> decorator =
        (handler, params, proceed) -> "decorated(" + proceed.get() + ")";
    McpDispatchInterceptor<String, String> claimer = (handler, params, proceed) -> "claimed";

    List<McpDispatchInterceptor<String, String>> sorted =
        DispatchChains.sort(List.of(decorator, claimer));
    Object result = DispatchChains.run(sorted, "h", "p", () -> "terminal");

    assertThat(result).isEqualTo("decorated(claimed)");
  }

  @Test
  void short_circuit_interceptor_skips_the_handler_entirely() {
    List<String> terminalCalls = new ArrayList<>();
    Supplier<Object> terminal =
        () -> {
          terminalCalls.add("called");
          return "terminal";
        };
    McpDispatchInterceptor<String, String> shortCircuit = (handler, params, proceed) -> "short";

    Object result = DispatchChains.run(List.of(shortCircuit), "h", "p", terminal);

    assertThat(result).isEqualTo("short");
    assertThat(terminalCalls).isEmpty();
  }

  // --- Zero-interceptor identity across all three services ------------------------------------

  private static List<CallToolHandler> toolHandlers() {
    DefaultMethodSchemaGenerator generator =
        new DefaultMethodSchemaGenerator(MAPPER, SchemaVersion.DRAFT_2020_12);
    Object target = new HelloTool();
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpTool.class).stream()
        .map(
            m ->
                CallToolHandlers.build(
                    target,
                    m,
                    new CallToolHandlers.BuildParams(generator, MAPPER, List.of(), s -> s, false)))
        .toList();
  }

  @Test
  void zero_interceptors_on_tools_service_is_the_default_path() {
    McpToolsService withEmpty =
        new McpToolsService(
            toolHandlers(),
            MAPPER,
            engine(),
            McpToolsService.DEFAULT_PAGE_SIZE,
            CacheSettings.defaults(),
            List.of());
    McpToolsService withoutParam = new McpToolsService(toolHandlers(), MAPPER, engine());

    var params =
        new CallToolRequestParams(
            HELLO_TOOL_NAME, MAPPER.createObjectNode().put("name", "World"), null, null, null);

    CallToolResult withEmptyResult = (CallToolResult) withEmpty.callTool(params);
    CallToolResult withoutParamResult = (CallToolResult) withoutParam.callTool(params);

    assertThat(withEmptyResult.structuredContent())
        .isEqualTo(withoutParamResult.structuredContent());
    assertThat(withEmptyResult.isError()).isEqualTo(withoutParamResult.isError());
  }

  private static GetPromptHandler promptHandler() {
    Prompt descriptor =
        new Prompt(
            "alpha-prompt",
            "alpha-prompt",
            "Alpha desc",
            null,
            List.of(new PromptArgument("arg1", "An argument", true)));
    return new GetPromptHandler(
        descriptor,
        null,
        null,
        (Map<String, String> args) -> {
          String arg1 = args.getOrDefault("arg1", "default");
          return new GetPromptResult(
              "Alpha desc",
              List.of(new PromptMessage(Role.USER, new TextContent(arg1, null))),
              ResultTypes.COMPLETE);
        },
        List.of(),
        List.of());
  }

  @Test
  void zero_interceptors_on_prompts_service_is_the_default_path() {
    McpPromptsService withEmpty =
        new McpPromptsService(
            List.of(promptHandler()),
            engine(),
            McpPromptsService.DEFAULT_PAGE_SIZE,
            CacheSettings.defaults(),
            List.of());
    McpPromptsService withoutParam = new McpPromptsService(List.of(promptHandler()), engine());

    var params =
        new GetPromptRequestParams(
            "alpha-prompt", java.util.Map.of("arg1", "hello"), null, null, null);

    GetPromptResult withEmptyResult = (GetPromptResult) withEmpty.getPrompt(params);
    GetPromptResult withoutParamResult = (GetPromptResult) withoutParam.getPrompt(params);

    assertThat(withEmptyResult).isEqualTo(withoutParamResult);
  }

  private static ReadResourceHandler resourceHandler() {
    Resource descriptor = new Resource("test://a", "Resource A", "desc A", "text/plain");
    return new ReadResourceHandler(
        descriptor,
        null,
        null,
        ignored ->
            new ReadResourceResult(
                List.of(new TextResourceContents("test://a", "text/plain", "content of test://a")),
                0L,
                com.callibrity.mocapi.model.CacheScope.PRIVATE,
                ResultTypes.COMPLETE),
        List.of());
  }

  @Test
  void zero_interceptors_on_resources_service_is_the_default_path() {
    McpResourcesService withEmpty =
        new McpResourcesService(
            List.of(ResourceContributor.of(List.of(resourceHandler()), List.of())),
            engine(),
            McpResourcesService.DEFAULT_PAGE_SIZE,
            CacheSettings.defaults(),
            List.of());
    McpResourcesService withoutParam =
        new McpResourcesService(
            List.of(ResourceContributor.of(List.of(resourceHandler()), List.of())), engine());

    var params = new ResourceRequestParams("test://a", null, null, null);

    ReadResourceResult withEmptyResult = (ReadResourceResult) withEmpty.readResource(params);
    ReadResourceResult withoutParamResult = (ReadResourceResult) withoutParam.readResource(params);

    assertThat(withEmptyResult).isEqualTo(withoutParamResult);
  }
}
