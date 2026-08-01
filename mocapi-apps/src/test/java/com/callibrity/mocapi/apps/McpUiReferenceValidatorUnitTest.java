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
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Direct unit coverage for {@link McpUiReferenceValidator}, exercising each branch of the boot-time
 * cross-check without standing up a Spring context.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpUiReferenceValidatorUnitTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private McpUiReferenceValidator validator(McpToolsService tools, McpResourcesService resources) {
    ObjectProvider<McpToolsService> toolsProvider = provider(tools);
    ObjectProvider<McpResourcesService> resourcesProvider = provider(resources);
    return new McpUiReferenceValidator(toolsProvider, resourcesProvider);
  }

  /**
   * Minimal {@link ObjectProvider} stub — avoids mocking the generic interface (which would force
   * an unchecked-cast suppression). Only {@code getIfAvailable()} is exercised by the validator.
   */
  private <T> ObjectProvider<T> provider(T value) {
    return new ObjectProvider<T>() {
      @Override
      public T getObject() {
        if (value == null) {
          throw new NoSuchBeanDefinitionException("test-stub: none");
        }
        return value;
      }

      @Override
      public T getObject(Object... args) {
        return getObject();
      }

      @Override
      public T getIfAvailable() {
        return value;
      }
    };
  }

  private Tool tool(String name, ObjectNode meta) {
    return new Tool(name, name, name, mapper.createObjectNode(), null, meta);
  }

  private ObjectNode metaWithUiResource(String uri) {
    ObjectNode meta = mapper.createObjectNode();
    meta.putObject("ui").put("resourceUri", uri);
    return meta;
  }

  @Nested
  class When_a_service_is_missing {

    @Test
    void does_nothing_when_no_tools_service() {
      var resources = mock(McpResourcesService.class);
      var subject = validator(null, resources);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void does_nothing_when_no_resources_service() {
      var tools = mock(McpToolsService.class);
      var subject = validator(tools, null);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
  }

  @Nested
  class When_links_resolve {

    @Test
    void starts_when_every_ui_link_is_declared() {
      var tools = mock(McpToolsService.class);
      when(tools.allToolDescriptors())
          .thenReturn(List.of(tool("ok", metaWithUiResource("ui://a"))));
      var resources = mock(McpResourcesService.class);
      when(resources.resourceUris()).thenReturn(Set.of("ui://a"));

      var subject = validator(tools, resources);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
  }

  @Nested
  class When_a_tool_has_no_usable_link {

    @Test
    void skips_a_tool_with_null_meta() {
      var tools = mock(McpToolsService.class);
      when(tools.allToolDescriptors()).thenReturn(List.of(tool("plain", null)));
      var resources = mock(McpResourcesService.class);
      when(resources.resourceUris()).thenReturn(Set.of());

      var subject = validator(tools, resources);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void skips_a_tool_whose_meta_omits_the_ui_resource_uri() {
      ObjectNode meta = mapper.createObjectNode();
      meta.putObject("ui");
      var tools = mock(McpToolsService.class);
      when(tools.allToolDescriptors()).thenReturn(List.of(tool("plain", meta)));
      var resources = mock(McpResourcesService.class);
      when(resources.resourceUris()).thenReturn(Set.of());

      var subject = validator(tools, resources);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void skips_a_tool_whose_ui_resource_uri_is_an_explicit_null() {
      ObjectNode meta = mapper.createObjectNode();
      meta.putObject("ui").putNull("resourceUri");
      var tools = mock(McpToolsService.class);
      when(tools.allToolDescriptors()).thenReturn(List.of(tool("plain", meta)));
      var resources = mock(McpResourcesService.class);
      when(resources.resourceUris()).thenReturn(Set.of());

      var subject = validator(tools, resources);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void skips_a_tool_whose_ui_resource_uri_is_blank() {
      var tools = mock(McpToolsService.class);
      when(tools.allToolDescriptors()).thenReturn(List.of(tool("plain", metaWithUiResource("  "))));
      var resources = mock(McpResourcesService.class);
      when(resources.resourceUris()).thenReturn(Set.of());

      var subject = validator(tools, resources);
      assertThatCode(subject::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
  }

  @Nested
  class When_a_link_dangles {

    @Test
    void fails_fast_naming_the_tool_the_missing_uri_and_the_declared_set() {
      var tools = mock(McpToolsService.class);
      when(tools.allToolDescriptors())
          .thenReturn(List.of(tool("bad", metaWithUiResource("ui://nope"))));
      var resources = mock(McpResourcesService.class);
      when(resources.resourceUris()).thenReturn(Set.of("ui://declared"));

      var subject = validator(tools, resources);
      assertThatThrownBy(subject::afterSingletonsInstantiated)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("bad")
          .hasMessageContaining("ui://nope")
          .hasMessageContaining("ui://declared");
    }
  }
}
