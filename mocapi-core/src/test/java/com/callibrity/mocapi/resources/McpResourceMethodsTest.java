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
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpResourceMethodsTest {

  private final McpResource greetingResource =
      new McpResource() {
        @Override
        public Descriptor descriptor() {
          return new Descriptor("test://greeting", "Greeting", "A greeting", "text/plain");
        }

        @Override
        public ReadResourceResponse read() {
          return new ReadResourceResponse(
              List.of(new TextResourceContent("test://greeting", "text/plain", "Hello!")));
        }
      };

  private final McpResourceTemplate itemTemplate =
      new McpResourceTemplate() {
        @Override
        public Descriptor descriptor() {
          return new Descriptor("test://item/{id}", "Item Template", "An item", "application/json");
        }

        @Override
        public ReadResourceResponse read(Map<String, String> pathVariables) {
          return new ReadResourceResponse(
              List.of(
                  new TextResourceContent(
                      "test://item/" + pathVariables.get("id"),
                      "application/json",
                      "item " + pathVariables.get("id"))));
        }
      };

  private final ResourcesRegistry registry =
      new ResourcesRegistry(List.of(greetingResource), List.of(itemTemplate), 50);
  private final McpResourceMethods methods = new McpResourceMethods(registry);

  @Test
  void listResourcesShouldReturnResources() {
    var response = methods.listResources(null);

    assertThat(response.resources()).hasSize(1);
    assertThat(response.resources().getFirst().uri()).isEqualTo("test://greeting");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void listResourceTemplatesShouldReturnTemplates() {
    var response = methods.listResourceTemplates(null);

    assertThat(response.resourceTemplates()).hasSize(1);
    assertThat(response.resourceTemplates().getFirst().uriTemplate()).isEqualTo("test://item/{id}");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void readResourceShouldReturnContent() {
    var response = methods.readResource("test://greeting");

    assertThat(response.contents()).hasSize(1);
    assertThat(response.contents().getFirst()).isInstanceOf(TextResourceContent.class);
    assertThat(((TextResourceContent) response.contents().getFirst()).text()).isEqualTo("Hello!");
  }

  @Test
  void readResourceWithUnknownUriShouldThrow() {
    assertThatThrownBy(() -> methods.readResource("test://unknown"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Resource not found");
  }

  @Test
  void subscribeShouldReturnEmptyMap() {
    var result = methods.subscribe("test://greeting");
    assertThat(result).isEmpty();
  }

  @Test
  void unsubscribeShouldReturnEmptyMap() {
    var result = methods.unsubscribe("test://greeting");
    assertThat(result).isEmpty();
  }
}
