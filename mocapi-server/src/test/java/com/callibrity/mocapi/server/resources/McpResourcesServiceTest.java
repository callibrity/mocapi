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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpResourcesServiceTest {

  private McpResourcesService service;

  private static ReadResourceHandler handler(
      String uri, String name, String description, String mimeType) {
    Resource descriptor = new Resource(uri, name, description, mimeType);
    return new ReadResourceHandler(
        descriptor,
        null,
        null,
        ignored ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uri, mimeType, "content of " + uri))),
        List.of());
  }

  private static ReadResourceTemplateHandler templateHandler(
      String uriTemplate, String name, String description, String mimeType) {
    ResourceTemplate descriptor = new ResourceTemplate(uriTemplate, name, description, mimeType);
    return new ReadResourceTemplateHandler(
        descriptor,
        null,
        null,
        vars ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uriTemplate, mimeType, "template " + vars))),
        List.of(),
        List.of());
  }

  @BeforeEach
  void setUp() {
    service =
        new McpResourcesService(
            List.of(
                handler("test://b", "Resource B", "desc B", "text/plain"),
                handler("test://a", "Resource A", "desc A", "text/plain")),
            List.of(
                templateHandler("test://items/{id}", "Item Template", "desc", "application/json")));
  }

  @Test
  void list_resources_returns_sorted_descriptors() {
    var result = service.listResources(null);

    assertThat(result.resources()).hasSize(2);
    assertThat(result.resources().get(0).uri()).isEqualTo("test://a");
    assertThat(result.resources().get(1).uri()).isEqualTo("test://b");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void list_resource_templates_returns_sorted_descriptors() {
    var result = service.listResourceTemplates(null);

    assertThat(result.resourceTemplates()).hasSize(1);
    assertThat(result.resourceTemplates().getFirst().uriTemplate()).isEqualTo("test://items/{id}");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void read_resource_by_exact_uri() {
    var params = new ResourceRequestParams("test://a", null);

    var result = service.readResource(params);

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.uri()).isEqualTo("test://a");
    assertThat(content.text()).isEqualTo("content of test://a");
  }

  @Test
  void read_resource_by_template_match() {
    var params = new ResourceRequestParams("test://items/42", null);

    var result = service.readResource(params);

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).contains("42");
  }

  @Test
  void exact_match_takes_precedence_over_template() {
    var exact = handler("test://items/special", "Special", "desc", "text/plain");
    var template = templateHandler("test://items/{id}", "Item", "desc", "application/json");
    var svc = new McpResourcesService(List.of(exact), List.of(template));

    var result = svc.readResource(new ResourceRequestParams("test://items/special", null));

    assertThat(((TextResourceContents) result.contents().getFirst()).text())
        .isEqualTo("content of test://items/special");
  }

  @Test
  void read_resource_throws_for_unknown_uri() {
    var params = new ResourceRequestParams("test://unknown", null);

    assertThatThrownBy(() -> service.readResource(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Resource not found: test://unknown");
  }

  @Test
  void is_empty_returns_true_when_no_resources_or_templates() {
    var emptyService = new McpResourcesService(List.of(), List.of());
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void is_empty_returns_false_when_resources_exist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void is_empty_returns_false_with_only_templates() {
    var svc =
        new McpResourcesService(
            List.of(), List.of(templateHandler("test://t/{id}", "T", "desc", "text/plain")));
    assertThat(svc.isEmpty()).isFalse();
  }

  @Test
  void pagination_works_for_resources() {
    List<ReadResourceHandler> handlers =
        IntStream.range(0, 5)
            .mapToObj(i -> handler(String.format("test://r%03d", i), "R" + i, "desc", "text/plain"))
            .toList();
    var svc = new McpResourcesService(handlers, List.of(), 2);

    var page1 = svc.listResources(null);
    assertThat(page1.resources()).hasSize(2);
    assertThat(page1.resources().getFirst().uri()).isEqualTo("test://r000");
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = svc.listResources(new PaginatedRequestParams(page1.nextCursor(), null));
    assertThat(page2.resources()).hasSize(2);
    assertThat(page2.resources().getFirst().uri()).isEqualTo("test://r002");
    assertThat(page2.nextCursor()).isNotNull();

    var page3 = svc.listResources(new PaginatedRequestParams(page2.nextCursor(), null));
    assertThat(page3.resources()).hasSize(1);
    assertThat(page3.resources().getFirst().uri()).isEqualTo("test://r004");
    assertThat(page3.nextCursor()).isNull();
  }

  @Test
  void pagination_works_for_templates() {
    List<ReadResourceTemplateHandler> templates =
        IntStream.range(0, 3)
            .mapToObj(
                i ->
                    templateHandler(
                        String.format("test://t%03d/{id}", i), "T" + i, "desc", "text/plain"))
            .toList();
    var svc = new McpResourcesService(List.of(), templates, 2);

    var page1 = svc.listResourceTemplates(null);
    assertThat(page1.resourceTemplates()).hasSize(2);
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = svc.listResourceTemplates(new PaginatedRequestParams(page1.nextCursor(), null));
    assertThat(page2.resourceTemplates()).hasSize(1);
    assertThat(page2.nextCursor()).isNull();
  }

  @Test
  void invalid_cursor_throws_exception() {
    var params = new PaginatedRequestParams("not-valid-base64!!!", null);
    assertThatThrownBy(() -> service.listResources(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void duplicate_uri_template_throws_exception() {
    var t1 = templateHandler("test://items/{id}", "T1", "first", "text/plain");
    var t2 = templateHandler("test://items/{id}", "T2", "duplicate", "text/plain");

    List<ReadResourceTemplateHandler> templates = List.of(t1, t2);
    List<ReadResourceHandler> emptyHandlers = List.of();
    assertThatThrownBy(() -> new McpResourcesService(emptyHandlers, templates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate URI template");
  }

  @Test
  void duplicate_resource_uri_throws_exception() {
    var a1 = handler("test://dup", "A", "first", "text/plain");
    var a2 = handler("test://dup", "B", "second", "text/plain");
    List<ReadResourceHandler> handlers = List.of(a1, a2);
    List<ReadResourceTemplateHandler> emptyTemplates = List.of();
    assertThatThrownBy(() -> new McpResourcesService(handlers, emptyTemplates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate resource URI");
  }

  @Test
  void out_of_range_cursor_returns_empty_page() {
    var svc =
        new McpResourcesService(
            List.of(handler("test://a", "A", "desc", "text/plain")), List.of(), 2);

    var largeOffset =
        java.util.Base64.getEncoder()
            .encodeToString(java.nio.ByteBuffer.allocate(4).putInt(100).array());
    var result = svc.listResources(new PaginatedRequestParams(largeOffset, null));

    assertThat(result.resources()).isEmpty();
    assertThat(result.nextCursor()).isNull();
  }

  private static ReadResourceHandler guardedHandler(String uri, Guard guard) {
    return new ReadResourceHandler(
        new Resource(uri, "g", "g", "text/plain"),
        null,
        null,
        ignored -> new ReadResourceResult(List.of(new TextResourceContents(uri, "text/plain", ""))),
        List.of(guard));
  }

  private static ReadResourceTemplateHandler guardedTemplateHandler(
      String uriTemplate, Guard guard) {
    return new ReadResourceTemplateHandler(
        new ResourceTemplate(uriTemplate, "g", "g", "text/plain"),
        null,
        null,
        vars ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uriTemplate, "text/plain", ""))),
        List.of(),
        List.of(guard));
  }

  // Call-time denial (guardedHandler + readResource) moved to GuardEvaluationInterceptor;
  // covered by GuardEvaluationInterceptorTest and the per-kind chain-ordering tests.

  @Test
  void denied_resource_and_template_are_absent_from_list() {
    var svc =
        new McpResourcesService(
            List.of(
                guardedHandler("file:///visible", () -> new GuardDecision.Allow()),
                guardedHandler("file:///hidden", () -> new GuardDecision.Deny("x"))),
            List.of(
                guardedTemplateHandler("file:///tpl/{a}", () -> new GuardDecision.Allow()),
                guardedTemplateHandler("file:///tpl2/{a}", () -> new GuardDecision.Deny("y"))));
    var resourceUris = svc.listResources(null).resources().stream().map(Resource::uri).toList();
    assertThat(resourceUris).contains("file:///visible").doesNotContain("file:///hidden");
    var templateUris =
        svc.listResourceTemplates(null).resourceTemplates().stream()
            .map(ResourceTemplate::uriTemplate)
            .toList();
    assertThat(templateUris).contains("file:///tpl/{a}").doesNotContain("file:///tpl2/{a}");
  }

  @Test
  void template_handler_read_receives_path_variables() {
    var params = new ResourceRequestParams("test://items/abc", null);
    var content = (TextResourceContents) service.readResource(params).contents().getFirst();
    assertThat(content.text()).contains("id=abc");
  }
}
