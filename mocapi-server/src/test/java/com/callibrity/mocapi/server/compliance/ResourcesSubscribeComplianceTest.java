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

import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Resources — Subscriptions.
 *
 * <p>Verifies resources/subscribe and resources/unsubscribe return success.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourcesSubscribeComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    var service = new McpResourcesService(List.of(), List.of());
    var dispatcher = buildDispatcher(MAPPER, service);
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, new ResourcesCapability(true, null), null),
            dispatcher);
  }

  @Test
  void subscribe_with_uri_returns_success() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("resources/subscribe", Map.of("uri", "file:///readme.md")),
        transport);

    var result = captureResult(transport);
    assertThat(result).isInstanceOf(JsonRpcResult.class);
  }

  @Test
  void unsubscribe_with_uri_returns_success() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("resources/unsubscribe", Map.of("uri", "file:///readme.md")),
        transport);

    var result = captureResult(transport);
    assertThat(result).isInstanceOf(JsonRpcResult.class);
  }
}
