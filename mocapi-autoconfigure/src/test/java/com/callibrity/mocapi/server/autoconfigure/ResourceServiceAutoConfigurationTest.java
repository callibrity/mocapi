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
package com.callibrity.mocapi.server.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.CompleteRequestParams;
import com.callibrity.mocapi.model.CompletionArgument;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplateReference;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ResourceDescriptorCustomizer;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AliasFor;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceServiceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PropertyPlaceholderAutoConfiguration.class,
                  MocapiServerResourcesAutoConfiguration.class,
                  MocapiServerAutoConfiguration.class))
          .withUserConfiguration(InfrastructureConfig.class);

  @Configuration(proxyBeanMethods = false)
  static class InfrastructureConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    JsonRpcDispatcher jsonRpcDispatcher() {
      return mock(JsonRpcDispatcher.class);
    }
  }

  static class SampleResourceService {

    @McpResource(uri = "test://hello", name = "Hello", mimeType = "text/plain")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }

    @McpResourceTemplate(uriTemplate = "test://items/{id}", name = "Item")
    public ReadResourceResult item(String id) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  enum Stage {
    DEV,
    STAGE,
    PROD
  }

  static class EnumResourceService {

    @McpResourceTemplate(uriTemplate = "env://{stage}/config", name = "Env Config")
    public ReadResourceResult config(Stage stage) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("env://" + stage + "/config", "text/plain", "cfg")),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  static class PlaceholderResourceService {

    @McpResource(
        uri = "${resource.uri}",
        name = "${resource.name}",
        description = "${resource.description}",
        mimeType = "${resource.mimeType}")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }

    @McpResourceTemplate(
        uriTemplate = "${template.uri}",
        name = "${template.name}",
        description = "${template.description}",
        mimeType = "${template.mimeType}")
    public ReadResourceResult item(String id) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  /**
   * A composed, meta-annotated resource annotation in the spirit of the planned
   * {@code @McpAppResource} (mocapi-apps): meta-annotated with {@link McpResource} (mimeType
   * defaulted to the MCP Apps type), with {@code uri}/{@code name} aliased through via {@link
   * AliasFor}. Exercises meta-annotation-aware discovery (ADR-0032) end to end, including
   * {@code @AliasFor} on {@code uri}.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @McpResource(uri = "", mimeType = "text/html;profile=mcp-app")
  @interface UiResourceFixture {

    @AliasFor(annotation = McpResource.class, attribute = "uri")
    String uri();

    @AliasFor(annotation = McpResource.class, attribute = "name")
    String name() default "";
  }

  static class MetaAnnotatedResourceService {

    @UiResourceFixture(uri = "ui://dashboard", name = "Dashboard")
    public ReadResourceResult dashboard() {
      return new ReadResourceResult(
          List.of(
              new TextResourceContents(
                  "ui://dashboard", "text/html;profile=mcp-app", "<html></html>")),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  @Test
  void discovers_meta_annotated_resource_and_merges_meta_annotation_attributes() {
    contextRunner
        .withBean(MetaAnnotatedResourceService.class, MetaAnnotatedResourceService::new)
        .run(
            context -> {
              var service = context.getBean(McpResourcesService.class);
              var resources = service.listResources(null).resources();
              assertThat(resources).hasSize(1);
              var resource = resources.getFirst();
              assertThat(resource.uri()).isEqualTo("ui://dashboard");
              assertThat(resource.name()).isEqualTo("Dashboard");
              assertThat(resource.mimeType()).isEqualTo("text/html;profile=mcp-app");
            });
  }

  @Test
  void discovers_resource_service_beans() {
    contextRunner
        .withBean(SampleResourceService.class, SampleResourceService::new)
        .run(
            context -> {
              var service = context.getBean(McpResourcesService.class);
              var resources = service.listResources(null).resources();
              assertThat(resources).hasSize(1);
              assertThat(resources.getFirst().uri()).isEqualTo("test://hello");

              var templates = service.listResourceTemplates(null).resourceTemplates();
              assertThat(templates).hasSize(1);
              assertThat(templates.getFirst().uriTemplate()).isEqualTo("test://items/{id}");
            });
  }

  @Test
  void returns_empty_when_no_resource_service_beans() {
    contextRunner.run(
        context -> {
          var service = context.getBean(McpResourcesService.class);
          assertThat(service.isEmpty()).isTrue();
        });
  }

  @Test
  void resolves_placeholders_in_resource_and_template_metadata() {
    contextRunner
        .withBean(PlaceholderResourceService.class, PlaceholderResourceService::new)
        .withPropertyValues(
            "resource.uri=test://resolved",
            "resource.name=Resolved Resource",
            "resource.description=Resolved resource description",
            "resource.mimeType=application/json",
            "template.uri=test://resolved/{id}",
            "template.name=Resolved Template",
            "template.description=Resolved template description",
            "template.mimeType=application/xml")
        .run(
            context -> {
              var service = context.getBean(McpResourcesService.class);

              var resource = service.listResources(null).resources().getFirst();
              assertThat(resource.uri()).isEqualTo("test://resolved");
              assertThat(resource.name()).isEqualTo("Resolved Resource");
              assertThat(resource.description()).isEqualTo("Resolved resource description");
              assertThat(resource.mimeType()).isEqualTo("application/json");

              var template = service.listResourceTemplates(null).resourceTemplates().getFirst();
              assertThat(template.uriTemplate()).isEqualTo("test://resolved/{id}");
              assertThat(template.name()).isEqualTo("Resolved Template");
              assertThat(template.description()).isEqualTo("Resolved template description");
              assertThat(template.mimeType()).isEqualTo("application/xml");
            });
  }

  @Test
  void fails_fast_when_placeholder_is_missing() {
    contextRunner
        .withBean(PlaceholderResourceService.class, PlaceholderResourceService::new)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("Could not resolve placeholder"));
  }

  @Test
  void registers_enum_template_variable_completions_with_completions_service() {
    contextRunner
        .withBean(EnumResourceService.class, EnumResourceService::new)
        .run(
            context -> {
              var completions = context.getBean(McpCompletionsService.class);
              var result =
                  completions.complete(
                      new CompleteRequestParams(
                          new ResourceTemplateReference("ref/resource", "env://{stage}/config"),
                          new CompletionArgument("stage", ""),
                          null,
                          null));
              assertThat(result.completion().values()).containsExactly("DEV", "STAGE", "PROD");
            });
  }

  @Test
  void read_resource_dispatches_through_handler() {
    contextRunner
        .withBean(SampleResourceService.class, SampleResourceService::new)
        .run(
            context -> {
              var service = context.getBean(McpResourcesService.class);
              var result =
                  (ReadResourceResult)
                      service.readResource(
                          new ResourceRequestParams("test://hello", null, null, null));
              var content = (TextResourceContents) result.contents().getFirst();
              assertThat(content.text()).isEqualTo("hi");
            });
  }

  @Test
  void applies_resource_descriptor_customizers() {
    ResourceDescriptorCustomizer stamp =
        (method, descriptor) ->
            descriptor.withMeta(new ObjectMapper().createObjectNode().put("k", "v"));
    contextRunner
        .withBean(SampleResourceService.class, SampleResourceService::new)
        .withBean(ResourceDescriptorCustomizer.class, () -> stamp)
        .run(
            context -> {
              var service = context.getBean(McpResourcesService.class);
              var resource = service.listResources(null).resources().getFirst();
              assertThat(resource.meta().path("k").asString()).isEqualTo("v");
            });
  }

  @Test
  void paginates_with_configured_page_size() {
    contextRunner
        .withBean(SampleResourceService.class, SampleResourceService::new)
        .withPropertyValues("mocapi.server.pagination.page-size=1")
        .run(
            context -> {
              var service = context.getBean(McpResourcesService.class);
              var page = service.listResources(new PaginatedRequestParams(null, null));
              assertThat(page.resources()).hasSize(1);
            });
  }
}
