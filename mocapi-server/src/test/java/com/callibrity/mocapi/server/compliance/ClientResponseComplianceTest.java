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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * MCP 2025-11-25 § Lifecycle — Client Responses.
 *
 * <p>Verifies handleResponse delivers responses to the correlation service. The correlation
 * service's internal Mailbox behavior (expiry, matching) is tested at the unit level in the
 * correlation service's own tests.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClientResponseComplianceTest {

  private McpServer server;
  private McpResponseCorrelationService correlationService;

  @BeforeEach
  void setUp() {
    correlationService = mock(McpResponseCorrelationService.class);
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, null),
            correlationService);
  }

  @Test
  void handle_response_with_result_delivers_to_correlation_service() {
    var response =
        new JsonRpcResult(
            MAPPER.createObjectNode().put("value", "ok"),
            JsonNodeFactory.instance.textNode("corr-1"));

    server.handleResponse(noSession(), response);

    verify(correlationService).deliver(response);
  }

  @Test
  void handle_response_with_error_delivers_to_correlation_service() {
    var response =
        new JsonRpcError(-32600, "Bad request", JsonNodeFactory.instance.textNode("corr-2"));

    server.handleResponse(noSession(), response);

    verify(correlationService).deliver(response);
  }

  @Test
  void handle_response_with_unknown_correlation_id_does_not_crash() {
    var response =
        new JsonRpcResult(
            MAPPER.createObjectNode(), JsonNodeFactory.instance.textNode("unknown-id"));

    server.handleResponse(noSession(), response);

    verify(correlationService).deliver(response);
  }
}
