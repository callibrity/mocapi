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
package com.callibrity.mocapi.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.util.Cursors;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResourcesRegistryTest {

  private McpResource resource(String uri, String name, String description, String mimeType) {
    return new McpResource() {
      @Override
      public Descriptor descriptor() {
        return new Descriptor(uri, name, description, mimeType);
      }

      @Override
      public ReadResourceResponse read() {
        return new ReadResourceResponse(
            List.of(new TextResourceContent(uri, mimeType, "content of " + uri)));
      }
    };
  }

  private McpResourceTemplate template(
      String uriTemplate, String name, String description, String mimeType) {
    return new McpResourceTemplate() {
      @Override
      public Descriptor descriptor() {
        return new Descriptor(uriTemplate, name, description, mimeType);
      }

      @Override
      public ReadResourceResponse read(Map<String, String> pathVariables) {
        return new ReadResourceResponse(
            List.of(new TextResourceContent(uriTemplate, mimeType, "template content")));
      }
    };
  }

  @Test
  void isEmptyShouldReturnTrueWhenNoResourcesOrTemplates() {
    var registry = new ResourcesRegistry(List.of(), List.of(), 50);
    assertThat(registry.isEmpty()).isTrue();
  }

  @Test
  void isEmptyShouldReturnFalseWhenResourcesExist() {
    var r = resource("test://a", "A", "desc", "text/plain");
    var registry = new ResourcesRegistry(List.of(r), List.of(), 50);
    assertThat(registry.isEmpty()).isFalse();
  }

  @Test
  void isEmptyShouldReturnFalseWhenTemplatesExist() {
    var t = template("test://t/{id}", "T", "desc", "text/plain");
    var registry = new ResourcesRegistry(List.of(), List.of(t), 50);
    assertThat(registry.isEmpty()).isFalse();
  }

  @Test
  void shouldListAllResources() {
    var resources =
        List.of(
            resource("test://b", "Resource B", "desc B", "text/plain"),
            resource("test://a", "Resource A", "desc A", "text/plain"));
    var registry = new ResourcesRegistry(resources, List.of(), 50);

    var response = registry.listResources(null);

    assertThat(response.resources()).hasSize(2);
    assertThat(response.resources().get(0).uri()).isEqualTo("test://a");
    assertThat(response.resources().get(1).uri()).isEqualTo("test://b");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldListResourceTemplates() {
    var templates =
        List.of(
            template("test://b/{id}", "Template B", "desc", "text/plain"),
            template("test://a/{id}", "Template A", "desc", "text/plain"));
    var registry = new ResourcesRegistry(List.of(), templates, 50);

    var response = registry.listResourceTemplates(null);

    assertThat(response.resourceTemplates()).hasSize(2);
    assertThat(response.resourceTemplates().get(0).uriTemplate()).isEqualTo("test://a/{id}");
    assertThat(response.resourceTemplates().get(1).uriTemplate()).isEqualTo("test://b/{id}");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldReadResourceByExactUriMatch() {
    var resources = List.of(resource("test://hello", "Hello", "desc", "text/plain"));
    var registry = new ResourcesRegistry(resources, List.of(), 50);

    var response = registry.readResource("test://hello");

    assertThat(response.contents()).hasSize(1);
    var content = (TextResourceContent) response.contents().getFirst();
    assertThat(content.uri()).isEqualTo("test://hello");
    assertThat(content.text()).isEqualTo("content of test://hello");
  }

  @Test
  void shouldReadResourceByTemplateMatch() {
    var t = template("test://item/{id}/data", "Item", "desc", "application/json");
    var registry = new ResourcesRegistry(List.of(), List.of(t), 50);

    var response = registry.readResource("test://item/42/data");

    assertThat(response.contents()).hasSize(1);
    assertThat(((TextResourceContent) response.contents().getFirst()).text())
        .isEqualTo("template content");
  }

  @Test
  void exactMatchShouldTakePrecedenceOverTemplateMatch() {
    var r = resource("test://item/special/data", "Special", "desc", "text/plain");
    var t = template("test://item/{id}/data", "Item", "desc", "application/json");
    var registry = new ResourcesRegistry(List.of(r), List.of(t), 50);

    var response = registry.readResource("test://item/special/data");

    assertThat(((TextResourceContent) response.contents().getFirst()).text())
        .isEqualTo("content of test://item/special/data");
  }

  @Test
  void shouldThrowWhenReadingUnknownUri() {
    var resources = List.of(resource("test://hello", "Hello", "desc", "text/plain"));
    var registry = new ResourcesRegistry(resources, List.of(), 50);

    assertThatThrownBy(() -> registry.readResource("test://unknown"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Resource not found: test://unknown");
  }

  @Test
  void shouldPaginateResources() {
    List<McpResource> resources =
        IntStream.range(0, 5)
            .mapToObj(
                i -> resource(String.format("test://r%03d", i), "R" + i, "desc", "text/plain"))
            .toList();
    var registry = new ResourcesRegistry(resources, List.of(), 2);

    var page1 = registry.listResources(null);
    assertThat(page1.resources()).hasSize(2);
    assertThat(page1.resources().getFirst().uri()).isEqualTo("test://r000");
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = registry.listResources(page1.nextCursor());
    assertThat(page2.resources()).hasSize(2);
    assertThat(page2.resources().getFirst().uri()).isEqualTo("test://r002");
    assertThat(page2.nextCursor()).isNotNull();

    var page3 = registry.listResources(page2.nextCursor());
    assertThat(page3.resources()).hasSize(1);
    assertThat(page3.resources().getFirst().uri()).isEqualTo("test://r004");
    assertThat(page3.nextCursor()).isNull();
  }

  @Test
  void shouldPaginateResourceTemplates() {
    List<McpResourceTemplate> templates =
        IntStream.range(0, 3)
            .mapToObj(
                i -> template(String.format("test://t%03d/{id}", i), "T" + i, "desc", "text/plain"))
            .toList();
    var registry = new ResourcesRegistry(List.of(), templates, 2);

    var page1 = registry.listResourceTemplates(null);
    assertThat(page1.resourceTemplates()).hasSize(2);
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = registry.listResourceTemplates(page1.nextCursor());
    assertThat(page2.resourceTemplates()).hasSize(1);
    assertThat(page2.nextCursor()).isNull();
  }

  @Test
  void shouldThrowOnInvalidCursor() {
    var registry =
        new ResourcesRegistry(
            List.of(resource("test://a", "A", "desc", "text/plain")), List.of(), 2);

    assertThatThrownBy(() -> registry.listResources("not-valid-base64!!!"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void outOfRangeCursorReturnsEmptyPage() {
    var registry =
        new ResourcesRegistry(
            List.of(resource("test://a", "A", "desc", "text/plain")), List.of(), 2);
    String cursor = Cursors.encode(100);

    var response = registry.listResources(cursor);
    assertThat(response.resources()).isEmpty();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void cursorEncodingRoundTrips() {
    assertThat(Cursors.decode(Cursors.encode(42))).isEqualTo(42);
  }
}
