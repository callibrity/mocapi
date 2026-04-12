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
import com.callibrity.mocapi.server.prompts.McpPrompt;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Prompts — Getting.
 *
 * <p>Verifies prompts/get: returns messages, substitutes arguments, and handles unknown prompts.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PromptsGetComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    McpPrompt greetPrompt =
        new McpPrompt() {
          @Override
          public Prompt descriptor() {
            return new Prompt(
                "greet",
                null,
                "Greeting",
                null,
                List.of(new PromptArgument("name", "Person's name", true)));
          }

          @Override
          public GetPromptResult get(Map<String, String> arguments) {
            String name = arguments.getOrDefault("name", "World");
            return new GetPromptResult(
                "Greeting",
                List.of(new PromptMessage(Role.USER, new TextContent("Hello " + name, null))));
          }
        };

    var service = new McpPromptsService(List.of(() -> List.of(greetPrompt)));
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, new PromptsCapability(null)),
            service);
  }

  @Test
  void get_with_valid_name_returns_messages() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("prompts/get", Map.of("name", "greet", "arguments", Map.of("name", "Alice"))),
        transport);

    var result = captureResult(transport);
    var messages = result.result().path("messages");
    assertThat(messages.isArray()).isTrue();
    assertThat(messages.size()).isGreaterThan(0);
  }

  @Test
  void arguments_substituted_into_messages() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("prompts/get", Map.of("name", "greet", "arguments", Map.of("name", "Bob"))),
        transport);

    var result = captureResult(transport);
    var text = result.result().path("messages").get(0).path("content").path("text").asString();
    assertThat(text).contains("Bob");
  }

  @Test
  void unknown_prompt_name_returns_error() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId), call("prompts/get", Map.of("name", "nonexistent")), transport);

    var error = captureError(transport);
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
  }
}
