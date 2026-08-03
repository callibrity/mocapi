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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpResourcesServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static MrtrElicitationEngine engine() {
    return new MrtrElicitationEngine(
        RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, MAPPER), MAPPER);
  }

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
                List.of(new TextResourceContents(uri, mimeType, "content of " + uri)),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE),
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
                List.of(new TextResourceContents(uriTemplate, mimeType, "template " + vars)),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE),
        List.of(),
        List.of());
  }

  private static McpResourcesService service(
      List<ReadResourceHandler> handlers,
      List<ReadResourceTemplateHandler> templates,
      MrtrElicitationEngine engine) {
    return new McpResourcesService(List.of(ResourceContributor.of(handlers, templates)), engine);
  }

  private static McpResourcesService service(
      List<ReadResourceHandler> handlers,
      List<ReadResourceTemplateHandler> templates,
      MrtrElicitationEngine engine,
      int pageSize) {
    return new McpResourcesService(
        List.of(ResourceContributor.of(handlers, templates)),
        engine,
        pageSize,
        CacheSettings.defaults(),
        List.of());
  }

  private static McpResourcesService service(
      List<ReadResourceHandler> handlers,
      List<ReadResourceTemplateHandler> templates,
      MrtrElicitationEngine engine,
      int pageSize,
      CacheSettings cacheSettings) {
    return new McpResourcesService(
        List.of(ResourceContributor.of(handlers, templates)),
        engine,
        pageSize,
        cacheSettings,
        List.of());
  }

  @BeforeEach
  void setUp() {
    service =
        service(
            List.of(
                handler("test://b", "Resource B", "desc B", "text/plain"),
                handler("test://a", "Resource A", "desc A", "text/plain")),
            List.of(
                templateHandler("test://items/{id}", "Item Template", "desc", "application/json")),
            engine());
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
    var params = new ResourceRequestParams("test://a", null, null, null);

    var result = (ReadResourceResult) service.readResource(params);

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.uri()).isEqualTo("test://a");
    assertThat(content.text()).isEqualTo("content of test://a");
  }

  @Test
  void read_resource_by_template_match() {
    var params = new ResourceRequestParams("test://items/42", null, null, null);

    var result = (ReadResourceResult) service.readResource(params);

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).contains("42");
  }

  @Test
  void exact_match_takes_precedence_over_template() {
    var exact = handler("test://items/special", "Special", "desc", "text/plain");
    var template = templateHandler("test://items/{id}", "Item", "desc", "application/json");
    var svc = service(List.of(exact), List.of(template), engine());

    var result =
        (ReadResourceResult)
            svc.readResource(new ResourceRequestParams("test://items/special", null, null, null));

    assertThat(((TextResourceContents) result.contents().getFirst()).text())
        .isEqualTo("content of test://items/special");
  }

  @Test
  void read_resource_throws_for_unknown_uri() {
    var params = new ResourceRequestParams("test://unknown", null, null, null);

    assertThatThrownBy(() -> service.readResource(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Resource not found: test://unknown");
  }

  @Test
  void is_empty_returns_true_when_no_resources_or_templates() {
    var emptyService = service(List.of(), List.of(), engine());
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void is_empty_returns_false_when_resources_exist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void is_empty_returns_false_with_only_templates() {
    var svc =
        service(
            List.of(),
            List.of(templateHandler("test://t/{id}", "T", "desc", "text/plain")),
            engine());
    assertThat(svc.isEmpty()).isFalse();
  }

  @Test
  void pagination_works_for_resources() {
    List<ReadResourceHandler> handlers =
        IntStream.range(0, 5)
            .mapToObj(i -> handler(String.format("test://r%03d", i), "R" + i, "desc", "text/plain"))
            .toList();
    var svc = service(handlers, List.of(), engine(), 2);

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
    var svc = service(List.of(), templates, engine(), 2);

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
    var engine = engine();
    assertThatThrownBy(() -> service(emptyHandlers, templates, engine))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("both contribute URI template")
        .hasMessageContaining("test://items/{id}");
  }

  @Test
  void duplicate_resource_uri_throws_exception() {
    var a1 = handler("test://dup", "A", "first", "text/plain");
    var a2 = handler("test://dup", "B", "second", "text/plain");
    List<ReadResourceHandler> handlers = List.of(a1, a2);
    List<ReadResourceTemplateHandler> emptyTemplates = List.of();
    var engine = engine();
    assertThatThrownBy(() -> service(handlers, emptyTemplates, engine))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("both contribute resource URI")
        .hasMessageContaining("test://dup");
  }

  @Test
  void duplicate_resource_uri_from_two_contributors_names_both() {
    var a = handler("test://dup", "A", "first", "text/plain");
    var b = handler("test://dup", "B", "second", "text/plain");

    ResourceContributor first = ResourceContributor.of(List.of(a), List.of());

    class SecondContributor implements ResourceContributor {
      @Override
      public List<ReadResourceHandler> resources() {
        return List.of(b);
      }
    }
    ResourceContributor second = new SecondContributor();
    var contributors = List.of(first, second);
    var engine = engine();

    assertThatThrownBy(() -> new McpResourcesService(contributors, engine))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("test://dup")
        .hasMessageContaining(first.getClass().getName())
        .hasMessageContaining("SecondContributor");
  }

  @Test
  void out_of_range_cursor_returns_empty_page() {
    var svc =
        service(List.of(handler("test://a", "A", "desc", "text/plain")), List.of(), engine(), 2);

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
        ignored ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uri, "text/plain", "")),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE),
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
                List.of(new TextResourceContents(uriTemplate, "text/plain", "")),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE),
        List.of(),
        List.of(guard));
  }

  @Test
  void denied_resource_and_template_are_absent_from_list() {
    var svc =
        service(
            List.of(
                guardedHandler("file:///visible", GuardDecision.Allow::new),
                guardedHandler("file:///hidden", () -> new GuardDecision.Deny("x"))),
            List.of(
                guardedTemplateHandler("file:///tpl/{a}", GuardDecision.Allow::new),
                guardedTemplateHandler("file:///tpl2/{a}", () -> new GuardDecision.Deny("y"))),
            engine());
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
    var params = new ResourceRequestParams("test://items/abc", null, null, null);
    var content =
        (TextResourceContents)
            ((ReadResourceResult) service.readResource(params)).contents().getFirst();
    assertThat(content.text()).contains("id=abc");
  }

  @Nested
  class Cache_directives {

    private final CacheSettings settings =
        new CacheSettings(Duration.ofMinutes(2), Duration.ofSeconds(30), CacheScope.PUBLIC);

    private McpResourcesService configured(
        List<ReadResourceHandler> handlers, List<ReadResourceTemplateHandler> templates) {
      return service(
          handlers, templates, engine(), McpResourcesService.DEFAULT_PAGE_SIZE, settings);
    }

    @Test
    void defaults_are_zero_ttl_and_private_scope_on_all_three_results() {
      assertThat(service.listResources(null).ttlMs()).isZero();
      assertThat(service.listResources(null).cacheScope()).isEqualTo(CacheScope.PRIVATE);
      assertThat(service.listResourceTemplates(null).ttlMs()).isZero();
      assertThat(service.listResourceTemplates(null).cacheScope()).isEqualTo(CacheScope.PRIVATE);

      var read =
          (ReadResourceResult)
              service.readResource(new ResourceRequestParams("test://a", null, null, null));
      assertThat(read.ttlMs()).isZero();
      assertThat(read.cacheScope()).isEqualTo(CacheScope.PRIVATE);
    }

    @Test
    void list_resources_carries_configured_list_ttl_and_scope() {
      var svc = configured(List.of(handler("test://a", "A", "d", "text/plain")), List.of());

      var result = svc.listResources(null);

      assertThat(result.ttlMs()).isEqualTo(120_000L);
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PUBLIC);
      assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    }

    @Test
    void list_resource_templates_carries_configured_list_ttl_and_scope() {
      var svc =
          configured(List.of(), List.of(templateHandler("test://t/{id}", "T", "d", "text/plain")));

      var result = svc.listResourceTemplates(null);

      assertThat(result.ttlMs()).isEqualTo(120_000L);
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PUBLIC);
      assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    }

    @Test
    void read_resource_replaces_handler_defaults_with_configured_read_ttl_and_scope() {
      var svc = configured(List.of(handler("test://a", "A", "d", "text/plain")), List.of());

      var result =
          (ReadResourceResult)
              svc.readResource(new ResourceRequestParams("test://a", null, null, null));

      assertThat(result.ttlMs()).isEqualTo(30_000L);
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PUBLIC);
      assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    }

    @Test
    void read_resource_via_template_replaces_handler_defaults_with_configured_values() {
      var svc =
          configured(
              List.of(), List.of(templateHandler("test://items/{id}", "T", "d", "text/plain")));

      var result =
          (ReadResourceResult)
              svc.readResource(new ResourceRequestParams("test://items/42", null, null, null));

      assertThat(result.ttlMs()).isEqualTo(30_000L);
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PUBLIC);
    }

    @Test
    void read_resource_keeps_handler_supplied_explicit_cache_directives() {
      var explicit =
          new ReadResourceHandler(
              new Resource("test://explicit", "E", "d", "text/plain"),
              null,
              null,
              ignored ->
                  new ReadResourceResult(
                      List.of(new TextResourceContents("test://explicit", "text/plain", "x")),
                      5_000L,
                      CacheScope.PRIVATE,
                      ResultTypes.COMPLETE),
              List.of());
      var svc = configured(List.of(explicit), List.of());

      var result =
          (ReadResourceResult)
              svc.readResource(new ResourceRequestParams("test://explicit", null, null, null));

      assertThat(result.ttlMs()).isEqualTo(5_000L);
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PRIVATE);
    }
  }

  @Nested
  class Deterministic_ordering {

    @Test
    void list_resources_order_is_sorted_by_uri_regardless_of_registration_order() {
      var shuffled =
          service(
              List.of(
                  handler("test://delta", "D", "d", "text/plain"),
                  handler("test://alpha", "A", "d", "text/plain"),
                  handler("test://charlie", "C", "d", "text/plain"),
                  handler("test://bravo", "B", "d", "text/plain")),
              List.of(),
              engine());

      assertThat(shuffled.listResources(null).resources().stream().map(Resource::uri).toList())
          .containsExactly("test://alpha", "test://bravo", "test://charlie", "test://delta");
    }

    @Test
    void
        list_resource_templates_order_is_sorted_by_uri_template_regardless_of_registration_order() {
      var shuffled =
          service(
              List.of(),
              List.of(
                  templateHandler("test://c/{id}", "C", "d", "text/plain"),
                  templateHandler("test://a/{id}", "A", "d", "text/plain"),
                  templateHandler("test://b/{id}", "B", "d", "text/plain")),
              engine());

      assertThat(
              shuffled.listResourceTemplates(null).resourceTemplates().stream()
                  .map(ResourceTemplate::uriTemplate)
                  .toList())
          .containsExactly("test://a/{id}", "test://b/{id}", "test://c/{id}");
    }
  }
}
