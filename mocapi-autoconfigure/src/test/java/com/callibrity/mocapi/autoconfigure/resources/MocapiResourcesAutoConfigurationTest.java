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
package com.callibrity.mocapi.autoconfigure.resources;

import com.callibrity.mocapi.resources.McpResourceProvider;
import com.callibrity.mocapi.resources.McpResourcesCapability;
import com.callibrity.mocapi.resources.ReadResourceResult;
import com.callibrity.mocapi.resources.annotation.Resource;
import com.callibrity.mocapi.resources.annotation.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MocapiResourcesAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MocapiResourcesAutoConfiguration.class));

    @Test
    void shouldAutoConfigureResourcesCapability() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpResourcesCapability.class);
            assertThat(context).hasSingleBean(MocapiResourcesProperties.class);
        });
    }

    @Test
    void shouldAutoConfigureResourceServiceProvider() {
        contextRunner
                .withUserConfiguration(TestResourceServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ResourceServiceMcpResourceProvider.class);
                    var provider = context.getBean(ResourceServiceMcpResourceProvider.class);
                    assertThat(provider.getMcpResources()).hasSize(1);
                });
    }

    @Test
    void shouldConfigureResourceProviders() {
        contextRunner
                .withUserConfiguration(TestResourceProviderConfiguration.class)
                .run(context -> {
                    var capability = context.getBean(McpResourcesCapability.class);
                    var response = capability.listResources(null);
                    assertThat(response.resources()).hasSize(1);
                    assertThat(response.resources().get(0).name()).isEqualTo("Test Resource");
                });
    }

    @Test
    void shouldDisableWhenPropertySet() {
        contextRunner
                .withPropertyValues("mocapi.resources.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(McpResourcesCapability.class);
                });
    }

    @Configuration
    static class TestResourceServiceConfiguration {
        @Bean
        public TestResourceService testResourceService() {
            return new TestResourceService();
        }
    }

    @Configuration
    static class TestResourceProviderConfiguration {
        @Bean
        public McpResourceProvider testResourceProvider() {
            return () -> java.util.List.of(new TestMcpResource());
        }
    }

    @ResourceService
    static class TestResourceService {
        @Resource(name = "Test Resource")
        public ReadResourceResult getTestResource() {
            return ReadResourceResult.text("test content", "text/plain");
        }
    }

    static class TestMcpResource implements com.callibrity.mocapi.resources.McpResource {
        @Override
        public String uri() { return "test://resource"; }
        @Override
        public String name() { return "Test Resource"; }
        @Override
        public String title() { return "Test Resource"; }
        @Override
        public String description() { return "A test resource"; }
        @Override
        public String mimeType() { return "text/plain"; }
        @Override
        public ReadResourceResult read(java.util.Map<String, String> parameters) {
            return ReadResourceResult.text("test content", "text/plain");
        }
    }
}
