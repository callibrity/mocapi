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
package com.callibrity.mocapi.server.compliance;

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Resources — Listing.
 *
 * <p>Verifies resources/list and resources/templates/list return correct descriptors with
 * pagination.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourcesListComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    ReadResourceHandler fileResource =
        new ReadResourceHandler(
            new Resource("file:///readme.md", "readme", "The readme", "text/markdown"),
            null,
            null,
            ignored ->
                new ReadResourceResult(
                    List.of(new TextResourceContents("file:///readme.md", "text/markdown", "# Hi")),
                    0L,
                    CacheScope.PRIVATE,
                    ResultTypes.COMPLETE),
            List.of());

    ReadResourceHandler configResource =
        new ReadResourceHandler(
            new Resource("file:///config.json", "config", "Config file", "application/json"),
            null,
            null,
            ignored ->
                new ReadResourceResult(
                    List.of(
                        new TextResourceContents("file:///config.json", "application/json", "{}")),
                    0L,
                    CacheScope.PRIVATE,
                    ResultTypes.COMPLETE),
            List.of());

    ReadResourceTemplateHandler userTemplate =
        new ReadResourceTemplateHandler(
            new ResourceTemplate(
                "users://{userId}/profile", "User Profile", "Profile for user", "application/json"),
            null,
            null,
            pathVariables ->
                new ReadResourceResult(
                    List.of(
                        new TextResourceContents(
                            "users://" + pathVariables.get("userId") + "/profile",
                            "application/json",
                            "{}")),
                    0L,
                    CacheScope.PRIVATE,
                    ResultTypes.COMPLETE),
            List.of(),
            List.of());

    var resourcesService =
        new McpResourcesService(
            List.of(fileResource, configResource),
            List.of(userTemplate),
            ComplianceTestSupport.mrtrEngine());

    server = buildServer(resourcesService);
  }

  @Test
  void returns_all_registered_resources() {
    var transport = mock(McpTransport.class);

    server.handleCall(call("resources/list"), transport);

    var result = captureResult(transport);
    var resources = result.result().path("resources");
    assertThat(resources.isArray()).isTrue();
    assertThat(resources.size()).isEqualTo(2);
  }

  @Test
  void each_resource_has_uri_name_and_optional_description_and_mime_type() {
    var transport = mock(McpTransport.class);

    server.handleCall(call("resources/list"), transport);

    var result = captureResult(transport);
    var first = result.result().path("resources").get(0);
    assertThat(first.has("uri")).isTrue();
    assertThat(first.has("name")).isTrue();
    assertThat(first.has("description")).isTrue();
    assertThat(first.has("mimeType")).isTrue();
  }

  @Test
  void pagination_works() {
    ReadResourceHandler r1 = simpleResource("file:///a.txt", "a", "A");
    ReadResourceHandler r2 = simpleResource("file:///b.txt", "b", "B");
    ReadResourceHandler r3 = simpleResource("file:///c.txt", "c", "C");

    var pagedService =
        new McpResourcesService(
            List.of(r1, r2, r3), List.of(), ComplianceTestSupport.mrtrEngine(), 2);
    var pagedServer = buildServer(pagedService);

    var transport1 = mock(McpTransport.class);
    pagedServer.handleCall(call("resources/list"), transport1);
    var page1 = captureResult(transport1);
    assertThat(page1.result().path("resources").size()).isEqualTo(2);
    assertThat(page1.result().has("nextCursor")).isTrue();

    var cursor = page1.result().path("nextCursor").asString();
    var transport2 = mock(McpTransport.class);
    pagedServer.handleCall(call("resources/list", Map.of("cursor", cursor)), transport2);
    var page2 = captureResult(transport2);
    assertThat(page2.result().path("resources").size()).isEqualTo(1);
  }

  @Test
  void templates_list_returns_resource_templates() {
    var transport = mock(McpTransport.class);

    server.handleCall(call("resources/templates/list"), transport);

    var result = captureResult(transport);
    var templates = result.result().path("resourceTemplates");
    assertThat(templates.isArray()).isTrue();
    assertThat(templates.size()).isEqualTo(1);
    assertThat(templates.get(0).has("uriTemplate")).isTrue();
  }

  // --- helpers ---

  private static ReadResourceHandler simpleResource(String uri, String name, String description) {
    return new ReadResourceHandler(
        new Resource(uri, name, description, "text/plain"),
        null,
        null,
        ignored ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uri, "text/plain", "content")),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE),
        List.of());
  }
}
