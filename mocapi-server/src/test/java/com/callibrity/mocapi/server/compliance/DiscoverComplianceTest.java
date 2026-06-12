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

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.PROTOCOL_VERSION;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.buildServer;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.call;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.callWithMeta;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.callWithoutEnvelope;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.captureError;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.captureResult;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.model.UnsupportedProtocolVersionErrorData;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.discover.DiscoverHandler;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 § Lifecycle — server/discover.
 *
 * <p>Verifies the mandatory discover method: version/capability/identity advertisement, the
 * required cacheable-result fields, the REQUIRED {@code _meta} envelope (no envelope-less probe
 * exists), and the version-bootstrap flow — an unsupported-version discover returns {@code
 * UnsupportedProtocolVersionError} whose {@code data.supported} lists the server's versions.
 * Discover works with no prior request: there is no handshake.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DiscoverComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    var handler =
        new DiscoverHandler(
            new Implementation("test-server", "Test Server", "1.0.0", null),
            "Use the tools wisely.",
            new ServerCapabilities(
                null,
                new ToolsCapability(null),
                null,
                new CompletionsCapability(),
                null,
                null,
                Map.of()));
    server = buildServer(handler);
  }

  @Nested
  class With_a_valid_envelope {

    @Test
    void discover_works_as_the_very_first_request() {
      // No handshake exists — this is the only call the server ever sees.
      var transport = mock(McpTransport.class);

      server.handleCall(call(McpMethods.SERVER_DISCOVER), transport);

      var result = captureResult(transport).result();
      assertThat(result.path("supportedVersions").size()).isEqualTo(2);
      assertThat(result.path("supportedVersions").get(0).asString()).isEqualTo(PROTOCOL_VERSION);
      assertThat(result.path("supportedVersions").get(1).asString())
          .isEqualTo(McpServer.DRAFT_PROTOCOL_VERSION);
    }

    @Test
    void advertises_server_identity_and_instructions() {
      var transport = mock(McpTransport.class);

      server.handleCall(call(McpMethods.SERVER_DISCOVER), transport);

      var result = captureResult(transport).result();
      assertThat(result.path("serverInfo").path("name").asString()).isEqualTo("test-server");
      assertThat(result.path("serverInfo").path("version").asString()).isEqualTo("1.0.0");
      assertThat(result.path("instructions").asString()).isEqualTo("Use the tools wisely.");
    }

    @Test
    void advertises_capabilities_with_empty_extensions_and_no_logging() {
      var transport = mock(McpTransport.class);

      server.handleCall(call(McpMethods.SERVER_DISCOVER), transport);

      var capabilities = captureResult(transport).result().path("capabilities");
      assertThat(capabilities.has("tools")).isTrue();
      assertThat(capabilities.has("completions")).isTrue();
      assertThat(capabilities.has("logging")).isFalse();
      assertThat(capabilities.has("extensions")).isTrue();
      assertThat(capabilities.path("extensions").isEmpty()).isTrue();
    }

    @Test
    void carries_required_cacheable_result_fields() {
      var transport = mock(McpTransport.class);

      server.handleCall(call(McpMethods.SERVER_DISCOVER), transport);

      var result = captureResult(transport).result();
      assertThat(result.path("ttlMs").asLong()).isZero();
      assertThat(result.path("cacheScope").asString()).isEqualTo("private");
      assertThat(result.path("resultType").asString()).isEqualTo("complete");
    }
  }

  @Nested
  class With_an_unsupported_version {

    @Test
    void responds_with_unsupported_protocol_version_error_listing_supported_versions() {
      // This IS the version probe: a legacy or unknown client learns the supported list from
      // the error data and retries with a mutually supported version.
      var transport = mock(McpTransport.class);

      server.handleCall(
          callWithMeta(McpMethods.SERVER_DISCOVER, Map.of(), envelope("2025-11-25")), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(UnsupportedProtocolVersionErrorData.CODE);
      assertThat(error.data().path("supported").size()).isEqualTo(2);
      assertThat(error.data().path("supported").get(0).asString()).isEqualTo(PROTOCOL_VERSION);
      assertThat(error.data().path("supported").get(1).asString())
          .isEqualTo(McpServer.DRAFT_PROTOCOL_VERSION);
      assertThat(error.data().path("requested").asString()).isEqualTo("2025-11-25");
    }
  }

  @Nested
  class Without_the_envelope {

    @Test
    void discover_with_no_meta_envelope_is_invalid_params() {
      // The envelope is REQUIRED on discover like on every request — no envelope-less probe.
      var transport = mock(McpTransport.class);

      server.handleCall(callWithoutEnvelope(McpMethods.SERVER_DISCOVER, null), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
    }
  }

  @Nested
  class Legacy_clients {

    @Test
    void legacy_initialize_call_gets_a_clean_modern_error() {
      // ADR-0019: mocapi does not special-case the removed initialize method. A legacy client
      // POSTing initialize (with no modern envelope) gets invalid-params, not a crash.
      var transport = mock(McpTransport.class);

      server.handleCall(
          callWithoutEnvelope(
              "initialize",
              Map.of(
                  "protocolVersion", "2025-11-25",
                  "capabilities", Map.of(),
                  "clientInfo", Map.of("name", "old-client", "version", "0.1"))),
          transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
    }
  }
}
