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

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.buildServer;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.call;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.captureResult;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.inMemorySessionStore;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.initializeAndGetSessionId;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.initializeCall;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.noSession;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.withSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Utilities / Completion.
 *
 * <p>Verifies completion/complete returns prefix-filtered registered values for prompt arguments
 * and resource-template variables, and empty for unknown references.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompletionCompleteComplianceTest {

  private McpServer server;
  private McpCompletionsService completions;

  @BeforeEach
  void setUp() {
    completions = new McpCompletionsService();
    completions.registerPromptArgument(
        "summarize", "detail", List.of("BRIEF", "STANDARD", "DETAILED"));
    completions.registerResourceTemplateVariable(
        "env://{stage}/config", "stage", List.of("DEV", "STAGE", "PROD"));

    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, new CompletionsCapability(), null, null),
            completions);
  }

  @Test
  void complete_for_prompt_argument_returns_filtered_values() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call(
            "completion/complete",
            Map.of(
                "ref", Map.of("type", "ref/prompt", "name", "summarize"),
                "argument", Map.of("name", "detail", "value", "B"))),
        transport);

    var result = captureResult(transport);
    var values = result.result().path("completion").path("values");
    assertThat(values.isArray()).isTrue();
    assertThat(values.size()).isEqualTo(1);
    assertThat(values.get(0).asString()).isEqualTo("BRIEF");
  }

  @Test
  void complete_for_resource_template_variable_returns_registered_values() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call(
            "completion/complete",
            Map.of(
                "ref", Map.of("type", "ref/resource", "uri", "env://{stage}/config"),
                "argument", Map.of("name", "stage", "value", ""))),
        transport);

    var result = captureResult(transport);
    var values = result.result().path("completion").path("values");
    assertThat(values.size()).isEqualTo(3);
  }

  @Test
  void unknown_reference_returns_empty_values() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call(
            "completion/complete",
            Map.of(
                "ref", Map.of("type", "ref/prompt", "name", "unknown"),
                "argument", Map.of("name", "anything", "value", ""))),
        transport);

    var result = captureResult(transport);
    var values = result.result().path("completion").path("values");
    assertThat(values.isArray()).isTrue();
    assertThat(values.size()).isZero();
  }

  @Test
  void response_shape_is_always_compliant() {
    // Mirrors the three assertions the @modelcontextprotocol/conformance npx tool makes on
    // completion-complete: completion field present, values field present, values is an array.
    // We hit this with an unregistered ref to prove shape compliance even on the cold path.
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call(
            "completion/complete",
            Map.of(
                "ref", Map.of("type", "ref/prompt", "name", "test_prompt_with_arguments"),
                "argument", Map.of("name", "arg1", "value", "test"))),
        transport);

    var payload = captureResult(transport).result();
    assertThat(payload.has("completion")).as("completion field is present").isTrue();
    var completion = payload.path("completion");
    assertThat(completion.has("values")).as("completion.values field is present").isTrue();
    assertThat(completion.path("values").isArray()).as("completion.values is an array").isTrue();
  }

  @Test
  void initialize_advertises_completions_capability() {
    // MCP spec: servers supporting completion/complete must advertise capabilities.completions.
    // Anchors the end-to-end conformance story — the client picks the npx scenario list partly
    // from the initialize response, so failing to advertise would mean completion-complete gets
    // skipped even though the RPC works.
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    var payload = captureResult(transport).result();
    assertThat(payload.path("capabilities").has("completions"))
        .as("capabilities.completions is advertised")
        .isTrue();
  }
}
