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

import com.callibrity.mocapi.model.BlobResourceContents;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.resources.McpResource;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Resources — Reading.
 *
 * <p>Verifies resources/read: text resources, binary resources, and unknown URI handling.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourcesReadComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    McpResource textResource =
        new McpResource() {
          @Override
          public Resource descriptor() {
            return new Resource("file:///readme.md", "readme", "Readme", "text/markdown");
          }

          @Override
          public ReadResourceResult read() {
            return new ReadResourceResult(
                List.of(new TextResourceContents("file:///readme.md", "text/markdown", "# Hello")));
          }
        };

    McpResource blobResource =
        new McpResource() {
          @Override
          public Resource descriptor() {
            return new Resource("file:///image.png", "image", "An image", "image/png");
          }

          @Override
          public ReadResourceResult read() {
            var data =
                Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
            return new ReadResourceResult(
                List.of(new BlobResourceContents("file:///image.png", "image/png", data)));
          }
        };

    var service =
        new McpResourcesService(List.of(() -> List.of(textResource, blobResource)), List.of());

    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, new ResourcesCapability(null, null), null),
            service);
  }

  @Test
  void read_with_valid_uri_returns_content() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call("resources/read", Map.of("uri", "file:///readme.md")),
        transport);

    var result = captureResult(transport);
    var contents = result.result().path("contents");
    assertThat(contents.isArray()).isTrue();
    assertThat(contents.size()).isGreaterThan(0);
  }

  @Test
  void text_resource_returns_text_resource_contents() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call("resources/read", Map.of("uri", "file:///readme.md")),
        transport);

    var result = captureResult(transport);
    var first = result.result().path("contents").get(0);
    assertThat(first.has("text")).isTrue();
    assertThat(first.path("text").asString()).isEqualTo("# Hello");
  }

  @Test
  void binary_resource_returns_blob_resource_contents_with_base64() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call("resources/read", Map.of("uri", "file:///image.png")),
        transport);

    var result = captureResult(transport);
    var first = result.result().path("contents").get(0);
    assertThat(first.has("blob")).isTrue();
    var blobData = first.path("blob").asString();
    assertThat(Base64.getDecoder().decode(blobData)).isNotEmpty();
  }

  @Test
  void unknown_uri_returns_error() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId, server),
        call("resources/read", Map.of("uri", "file:///nonexistent")),
        transport);

    var error = captureError(transport);
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
  }
}
