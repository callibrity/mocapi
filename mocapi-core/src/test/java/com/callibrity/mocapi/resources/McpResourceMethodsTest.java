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

import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpResourceMethodsTest {

  private final McpResource greetingResource =
      new McpResource() {
        @Override
        public Resource descriptor() {
          return new Resource("test://greeting", "Greeting", "A greeting", "text/plain");
        }

        @Override
        public ReadResourceResult read() {
          return new ReadResourceResult(
              List.of(new TextResourceContents("test://greeting", "text/plain", "Hello!")));
        }
      };

  private final McpResourceTemplate itemTemplate =
      new McpResourceTemplate() {
        @Override
        public ResourceTemplate descriptor() {
          return new ResourceTemplate(
              "test://item/{id}", "Item Template", "An item", "application/json");
        }

        @Override
        public ReadResourceResult read(Map<String, String> pathVariables) {
          return new ReadResourceResult(
              List.of(
                  new TextResourceContents(
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
    assertThat(response.contents().getFirst()).isInstanceOf(TextResourceContents.class);
    assertThat(((TextResourceContents) response.contents().getFirst()).text()).isEqualTo("Hello!");
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
