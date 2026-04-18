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
package com.callibrity.mocapi.server.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.substrate.SubstrateTestSupport;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceServiceMcpResourceProviderTest {

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
    AtomFactory atomFactory() {
      return SubstrateTestSupport.atomFactory();
    }

    @Bean
    MailboxFactory mailboxFactory() {
      return SubstrateTestSupport.mailboxFactory();
    }

    @Bean
    JsonRpcDispatcher jsonRpcDispatcher() {
      return mock(JsonRpcDispatcher.class);
    }

    @Bean
    MethodInvokerFactory methodInvokerFactory() {
      return new DefaultMethodInvokerFactory(List.of());
    }
  }

  @ResourceService
  static class SampleResourceService {

    @ResourceMethod(uri = "test://hello", name = "Hello", mimeType = "text/plain")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")));
    }

    @ResourceTemplateMethod(uriTemplate = "test://items/{id}", name = "Item")
    public ReadResourceResult item(String id) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)));
    }
  }

  @ResourceService
  static class PlaceholderResourceService {

    @ResourceMethod(
        uri = "${resource.uri}",
        name = "${resource.name}",
        description = "${resource.description}",
        mimeType = "${resource.mimeType}")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")));
    }

    @ResourceTemplateMethod(
        uriTemplate = "${template.uri}",
        name = "${template.name}",
        description = "${template.description}",
        mimeType = "${template.mimeType}")
    public ReadResourceResult item(String id) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)));
    }
  }

  @Test
  void discovers_resource_service_beans() {
    contextRunner
        .withBean(SampleResourceService.class, SampleResourceService::new)
        .run(
            context -> {
              var provider = context.getBean(ResourceServiceMcpResourceProvider.class);
              assertThat(provider.getMcpResources()).hasSize(1);
              assertThat(provider.getMcpResources().getFirst().descriptor().uri())
                  .isEqualTo("test://hello");
              assertThat(provider.getMcpResourceTemplates()).hasSize(1);
              assertThat(provider.getMcpResourceTemplates().getFirst().descriptor().uriTemplate())
                  .isEqualTo("test://items/{id}");
            });
  }

  @Test
  void returns_empty_lists_when_no_resource_service_beans() {
    contextRunner.run(
        context -> {
          var provider = context.getBean(ResourceServiceMcpResourceProvider.class);
          assertThat(provider.getMcpResources()).isEmpty();
          assertThat(provider.getMcpResourceTemplates()).isEmpty();
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
              var provider = context.getBean(ResourceServiceMcpResourceProvider.class);

              var resource = provider.getMcpResources().getFirst();
              assertThat(resource.descriptor().uri()).isEqualTo("test://resolved");
              assertThat(resource.descriptor().name()).isEqualTo("Resolved Resource");
              assertThat(resource.descriptor().description())
                  .isEqualTo("Resolved resource description");
              assertThat(resource.descriptor().mimeType()).isEqualTo("application/json");

              var template = provider.getMcpResourceTemplates().getFirst();
              assertThat(template.descriptor().uriTemplate()).isEqualTo("test://resolved/{id}");
              assertThat(template.descriptor().name()).isEqualTo("Resolved Template");
              assertThat(template.descriptor().description())
                  .isEqualTo("Resolved template description");
              assertThat(template.descriptor().mimeType()).isEqualTo("application/xml");
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
                    .hasMessageContaining("resource.uri"));
  }

  @Test
  void returns_unmodifiable_lists() {
    contextRunner
        .withBean(SampleResourceService.class, SampleResourceService::new)
        .run(
            context -> {
              var provider = context.getBean(ResourceServiceMcpResourceProvider.class);
              assertThat(provider.getMcpResources()).isUnmodifiable();
              assertThat(provider.getMcpResourceTemplates()).isUnmodifiable();
            });
  }
}
