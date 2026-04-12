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

import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Utilities — Completion.
 *
 * <p>Verifies completion/complete returns completion values.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompletionComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    var service = new McpCompletionsService();
    var dispatcher = buildDispatcher(MAPPER, service);
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, new CompletionsCapability(), null, null),
            dispatcher);
  }

  @Test
  void complete_returns_completion_values() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call(
            "completion/complete",
            Map.of(
                "ref", Map.of("type", "ref/prompt", "name", "test"),
                "argument", Map.of("name", "arg", "value", "val"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().has("completion")).isTrue();
    assertThat(result.result().path("completion").has("values")).isTrue();
  }
}
