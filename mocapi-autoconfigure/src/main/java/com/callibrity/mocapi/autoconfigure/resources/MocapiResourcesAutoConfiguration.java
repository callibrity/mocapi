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

import com.callibrity.mocapi.autoconfigure.MocapiAutoConfiguration;
import com.callibrity.mocapi.resources.McpResource;
import com.callibrity.mocapi.resources.McpResourceProvider;
import com.callibrity.mocapi.resources.McpResourcesCapability;
import com.callibrity.mocapi.resources.annotation.AnnotationMcpResourceProviderFactory;
import com.callibrity.mocapi.resources.annotation.DefaultAnnotationMcpResourceProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@AutoConfiguration
@AutoConfigureBefore(MocapiAutoConfiguration.class)
@ConditionalOnClass(McpResourcesCapability.class)
@EnableConfigurationProperties(MocapiResourcesProperties.class)
@PropertySource("classpath:mocapi-resources-defaults.properties")
@RequiredArgsConstructor
public class MocapiResourcesAutoConfiguration {

    private final MocapiResourcesProperties props;

    @Bean
    @ConditionalOnProperty(prefix = "mocapi.resources", name = "enabled", havingValue = "true", matchIfMissing = true)
    public McpResourcesCapability mcpResourcesCapability(List<McpResourceProvider> resourceProviders) {
        return new McpResourcesCapability(resourceProviders);
    }

    @Bean
    public McpResourceProvider mcpResourceBeansProvider(List<McpResource> beans) {
        return () -> List.copyOf(beans);
    }

    @Bean
    public AnnotationMcpResourceProviderFactory annotationMcpResourceProviderFactory() {
        return new DefaultAnnotationMcpResourceProviderFactory();
    }

    @Bean
    public ResourceServiceMcpResourceProvider resourceServiceMcpResourceProvider(ApplicationContext context) {
        return new ResourceServiceMcpResourceProvider(context);
    }
}
