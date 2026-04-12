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
package com.callibrity.mocapi.protocol.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpResourcesServiceTest {

  private McpResourcesService service;

  private static McpResource resource(
      String uri, String name, String description, String mimeType) {
    return new McpResource() {
      @Override
      public Resource descriptor() {
        return new Resource(uri, name, description, mimeType);
      }

      @Override
      public ReadResourceResult read() {
        return new ReadResourceResult(
            List.of(new TextResourceContents(uri, mimeType, "content of " + uri)));
      }
    };
  }

  private static McpResourceTemplate template(
      String uriTemplate, String name, String description, String mimeType) {
    return new McpResourceTemplate() {
      @Override
      public ResourceTemplate descriptor() {
        return new ResourceTemplate(uriTemplate, name, description, mimeType);
      }

      @Override
      public ReadResourceResult read(Map<String, String> pathVariables) {
        return new ReadResourceResult(
            List.of(new TextResourceContents(uriTemplate, mimeType, "template " + pathVariables)));
      }
    };
  }

  @BeforeEach
  void setUp() {
    var resourceProvider =
        (McpResourceProvider)
            () ->
                List.of(
                    resource("test://b", "Resource B", "desc B", "text/plain"),
                    resource("test://a", "Resource A", "desc A", "text/plain"));
    var templateProvider =
        (McpResourceTemplateProvider)
            () ->
                List.of(template("test://items/{id}", "Item Template", "desc", "application/json"));
    service = new McpResourcesService(List.of(resourceProvider), List.of(templateProvider));
  }

  @Test
  void listResourcesReturnsSortedDescriptors() {
    var result = service.listResources(null);

    assertThat(result.resources()).hasSize(2);
    assertThat(result.resources().get(0).uri()).isEqualTo("test://a");
    assertThat(result.resources().get(1).uri()).isEqualTo("test://b");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void listResourceTemplatesReturnsSortedDescriptors() {
    var result = service.listResourceTemplates(null);

    assertThat(result.resourceTemplates()).hasSize(1);
    assertThat(result.resourceTemplates().getFirst().uriTemplate()).isEqualTo("test://items/{id}");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void readResourceByExactUri() {
    var params = new ResourceRequestParams("test://a", null);

    var result = service.readResource(params);

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.uri()).isEqualTo("test://a");
    assertThat(content.text()).isEqualTo("content of test://a");
  }

  @Test
  void readResourceByTemplateMatch() {
    var params = new ResourceRequestParams("test://items/42", null);

    var result = service.readResource(params);

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).contains("42");
  }

  @Test
  void exactMatchTakesPrecedenceOverTemplate() {
    var exactResource = resource("test://items/special", "Special", "desc", "text/plain");
    var templateResource = template("test://items/{id}", "Item", "desc", "application/json");
    var svc =
        new McpResourcesService(
            List.of(() -> List.of(exactResource)), List.of(() -> List.of(templateResource)));

    var result = svc.readResource(new ResourceRequestParams("test://items/special", null));

    assertThat(((TextResourceContents) result.contents().getFirst()).text())
        .isEqualTo("content of test://items/special");
  }

  @Test
  void readResourceThrowsForUnknownUri() {
    var params = new ResourceRequestParams("test://unknown", null);

    assertThatThrownBy(() -> service.readResource(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Resource not found: test://unknown");
  }

  @Test
  void subscribeTracksUri() {
    var params = new ResourceRequestParams("test://a", null);

    assertThat(service.isSubscribed("test://a")).isFalse();

    service.subscribe(params);

    assertThat(service.isSubscribed("test://a")).isTrue();
  }

  @Test
  void unsubscribeRemovesTracking() {
    var params = new ResourceRequestParams("test://a", null);
    service.subscribe(params);

    service.unsubscribe(params);

    assertThat(service.isSubscribed("test://a")).isFalse();
  }

  @Test
  void isEmptyReturnsTrueWhenNoResourcesOrTemplates() {
    var emptyService = new McpResourcesService(List.of(), List.of());
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void isEmptyReturnsFalseWhenResourcesExist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void isEmptyReturnsFalseWithOnlyTemplates() {
    var svc =
        new McpResourcesService(
            List.of(),
            List.of(() -> List.of(template("test://t/{id}", "T", "desc", "text/plain"))));
    assertThat(svc.isEmpty()).isFalse();
  }

  @Test
  void paginationWorksForResources() {
    List<McpResource> resources =
        IntStream.range(0, 5)
            .mapToObj(
                i -> resource(String.format("test://r%03d", i), "R" + i, "desc", "text/plain"))
            .toList();
    var svc = new McpResourcesService(List.of(() -> resources), List.of(), 2);

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
  void paginationWorksForTemplates() {
    List<McpResourceTemplate> templates =
        IntStream.range(0, 3)
            .mapToObj(
                i -> template(String.format("test://t%03d/{id}", i), "T" + i, "desc", "text/plain"))
            .toList();
    var svc = new McpResourcesService(List.of(), List.of(() -> templates), 2);

    var page1 = svc.listResourceTemplates(null);
    assertThat(page1.resourceTemplates()).hasSize(2);
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = svc.listResourceTemplates(new PaginatedRequestParams(page1.nextCursor(), null));
    assertThat(page2.resourceTemplates()).hasSize(1);
    assertThat(page2.nextCursor()).isNull();
  }

  @Test
  void invalidCursorThrowsException() {
    assertThatThrownBy(
            () -> service.listResources(new PaginatedRequestParams("not-valid-base64!!!", null)))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void outOfRangeCursorReturnsEmptyPage() {
    var svc =
        new McpResourcesService(
            List.of(() -> List.of(resource("test://a", "A", "desc", "text/plain"))), List.of(), 2);

    // Encode a large offset manually via Base64
    var largeOffset =
        java.util.Base64.getEncoder()
            .encodeToString(java.nio.ByteBuffer.allocate(4).putInt(100).array());
    var result = svc.listResources(new PaginatedRequestParams(largeOffset, null));

    assertThat(result.resources()).isEmpty();
    assertThat(result.nextCursor()).isNull();
  }
}
