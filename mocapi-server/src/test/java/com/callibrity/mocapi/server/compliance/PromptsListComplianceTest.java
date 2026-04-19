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
package com.callibrity.mocapi.server.compliance;

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.prompts.GetPromptHandler;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Prompts — Listing.
 *
 * <p>Verifies prompts/list returns registered prompts with descriptors and pagination.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PromptsListComplianceTest {

  private McpServer server;

  private static GetPromptHandler handler(String name, List<PromptArgument> arguments) {
    Prompt descriptor = new Prompt(name, null, name + " prompt", null, arguments);
    return new GetPromptHandler(
        descriptor,
        null,
        null,
        args -> {
          @SuppressWarnings("unchecked")
          Map<String, String> typed = (Map<String, String>) args;
          return new GetPromptResult(
              name,
              List.of(
                  new PromptMessage(
                      Role.USER, new TextContent("Hello " + typed.get("name"), null))));
        },
        List.of());
  }

  @BeforeEach
  void setUp() {
    GetPromptHandler greet =
        handler("greet", List.of(new PromptArgument("name", "Person's name", true)));
    GetPromptHandler simple = handler("simple", null);
    var service = new McpPromptsService(List.of(greet, simple));
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, new PromptsCapability(null)),
            service);
  }

  @Test
  void returns_all_registered_prompts() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("prompts/list"), transport);

    var result = captureResult(transport);
    var prompts = result.result().path("prompts");
    assertThat(prompts.isArray()).isTrue();
    assertThat(prompts.size()).isEqualTo(2);
  }

  @Test
  void each_prompt_has_name_and_description() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("prompts/list"), transport);

    var result = captureResult(transport);
    var first = result.result().path("prompts").get(0);
    assertThat(first.has("name")).isTrue();
    assertThat(first.has("description")).isTrue();
  }

  @Test
  void prompt_with_arguments_includes_them() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("prompts/list"), transport);

    var result = captureResult(transport);
    var prompts = result.result().path("prompts");
    tools.jackson.databind.JsonNode greet = null;
    for (var p : prompts) {
      if ("greet".equals(p.path("name").asString())) {
        greet = p;
      }
    }
    assertThat(greet).isNotNull();
    assertThat(greet.has("arguments")).isTrue();
    assertThat(greet.path("arguments").size()).isEqualTo(1);
  }

  @Test
  void pagination_works() {
    var prompts = new java.util.ArrayList<GetPromptHandler>();
    for (int i = 0; i < 3; i++) {
      prompts.add(handler("prompt-" + i, null));
    }

    var service = new McpPromptsService(List.copyOf(prompts), 2);
    var pagedServer =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, new PromptsCapability(null)),
            service);

    var sessionId = initializeAndGetSessionId(pagedServer);
    var transport1 = mock(McpTransport.class);
    pagedServer.handleCall(withSession(sessionId, pagedServer), call("prompts/list"), transport1);
    var page1 = captureResult(transport1);
    assertThat(page1.result().path("prompts").size()).isEqualTo(2);
    assertThat(page1.result().has("nextCursor")).isTrue();
  }
}
