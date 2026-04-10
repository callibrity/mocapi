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

import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResourcesRegistryTest {

  private McpResourceProvider providerWithResources(List<McpResource> resources) {
    return new McpResourceProvider() {
      @Override
      public List<McpResource> getResources() {
        return resources;
      }

      @Override
      public List<McpResourceTemplate> getResourceTemplates() {
        return List.of();
      }

      @Override
      public ReadResourceResponse read(String uri) {
        return resources.stream()
            .filter(r -> r.uri().equals(uri))
            .findFirst()
            .map(
                r ->
                    new ReadResourceResponse(
                        List.of(
                            new ResourceContent(
                                r.uri(), r.mimeType(), "content of " + r.uri(), null))))
            .orElse(null);
      }
    };
  }

  private McpResourceProvider providerWithTemplates(List<McpResourceTemplate> templates) {
    return new McpResourceProvider() {
      @Override
      public List<McpResource> getResources() {
        return List.of();
      }

      @Override
      public List<McpResourceTemplate> getResourceTemplates() {
        return templates;
      }

      @Override
      public ReadResourceResponse read(String uri) {
        return null;
      }
    };
  }

  @Test
  void isEmptyShouldReturnTrueWhenNoProviders() {
    var registry = new ResourcesRegistry(List.of(), 50);
    assertThat(registry.isEmpty()).isTrue();
  }

  @Test
  void isEmptyShouldReturnFalseWhenProvidersExist() {
    var provider =
        providerWithResources(List.of(new McpResource("test://a", "A", "desc", "text/plain")));
    var registry = new ResourcesRegistry(List.of(provider), 50);
    assertThat(registry.isEmpty()).isFalse();
  }

  @Test
  void shouldListAllResources() {
    var resources =
        List.of(
            new McpResource("test://b", "Resource B", "desc B", "text/plain"),
            new McpResource("test://a", "Resource A", "desc A", "text/plain"));
    var registry = new ResourcesRegistry(List.of(providerWithResources(resources)), 50);

    var response = registry.listResources(null);

    assertThat(response.resources()).hasSize(2);
    assertThat(response.resources().get(0).uri()).isEqualTo("test://a");
    assertThat(response.resources().get(1).uri()).isEqualTo("test://b");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldAggregateResourcesFromMultipleProviders() {
    var provider1 =
        providerWithResources(List.of(new McpResource("test://a", "A", "desc", "text/plain")));
    var provider2 =
        providerWithResources(List.of(new McpResource("test://b", "B", "desc", "text/plain")));
    var registry = new ResourcesRegistry(List.of(provider1, provider2), 50);

    var response = registry.listResources(null);

    assertThat(response.resources()).hasSize(2);
    assertThat(response.resources().get(0).uri()).isEqualTo("test://a");
    assertThat(response.resources().get(1).uri()).isEqualTo("test://b");
  }

  @Test
  void shouldListResourceTemplates() {
    var templates =
        List.of(
            new McpResourceTemplate("test://b/{id}", "Template B", "desc", "text/plain"),
            new McpResourceTemplate("test://a/{id}", "Template A", "desc", "text/plain"));
    var registry = new ResourcesRegistry(List.of(providerWithTemplates(templates)), 50);

    var response = registry.listResourceTemplates(null);

    assertThat(response.resourceTemplates()).hasSize(2);
    assertThat(response.resourceTemplates().get(0).uriTemplate()).isEqualTo("test://a/{id}");
    assertThat(response.resourceTemplates().get(1).uriTemplate()).isEqualTo("test://b/{id}");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldReadResourceByUri() {
    var resources = List.of(new McpResource("test://hello", "Hello", "desc", "text/plain"));
    var registry = new ResourcesRegistry(List.of(providerWithResources(resources)), 50);

    var response = registry.readResource("test://hello");

    assertThat(response.contents()).hasSize(1);
    assertThat(response.contents().getFirst().uri()).isEqualTo("test://hello");
    assertThat(response.contents().getFirst().text()).isEqualTo("content of test://hello");
  }

  @Test
  void shouldThrowWhenReadingUnknownUri() {
    var resources = List.of(new McpResource("test://hello", "Hello", "desc", "text/plain"));
    var registry = new ResourcesRegistry(List.of(providerWithResources(resources)), 50);

    assertThatThrownBy(() -> registry.readResource("test://unknown"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Resource not found: test://unknown");
  }

  @Test
  void shouldPaginateResources() {
    List<McpResource> resources =
        IntStream.range(0, 5)
            .mapToObj(
                i ->
                    new McpResource(
                        String.format("test://r%03d", i), "R" + i, "desc", "text/plain"))
            .toList();
    var registry = new ResourcesRegistry(List.of(providerWithResources(resources)), 2);

    var page1 = registry.listResources(null);
    assertThat(page1.resources()).hasSize(2);
    assertThat(page1.resources().get(0).uri()).isEqualTo("test://r000");
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = registry.listResources(page1.nextCursor());
    assertThat(page2.resources()).hasSize(2);
    assertThat(page2.resources().get(0).uri()).isEqualTo("test://r002");
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
                i ->
                    new McpResourceTemplate(
                        String.format("test://t%03d/{id}", i), "T" + i, "desc", "text/plain"))
            .toList();
    var registry = new ResourcesRegistry(List.of(providerWithTemplates(templates)), 2);

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
            List.of(
                providerWithResources(
                    List.of(new McpResource("test://a", "A", "desc", "text/plain")))),
            2);

    assertThatThrownBy(() -> registry.listResources("not-valid-base64!!!"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void shouldThrowOnOutOfRangeCursor() {
    var registry =
        new ResourcesRegistry(
            List.of(
                providerWithResources(
                    List.of(new McpResource("test://a", "A", "desc", "text/plain")))),
            2);
    String cursor = ResourcesRegistry.encodeCursor(100);

    assertThatThrownBy(() -> registry.listResources(cursor))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void cursorEncodingRoundTrips() {
    assertThat(ResourcesRegistry.decodeCursor(ResourcesRegistry.encodeCursor(42))).isEqualTo(42);
  }

  @Test
  void shouldSubscribeAndUnsubscribe() {
    var registry = new ResourcesRegistry(List.of(), 50);

    assertThat(registry.isSubscribed("test://watched")).isFalse();
    registry.subscribe("test://watched");
    assertThat(registry.isSubscribed("test://watched")).isTrue();
    registry.unsubscribe("test://watched");
    assertThat(registry.isSubscribed("test://watched")).isFalse();
  }
}
