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
package com.callibrity.mocapi.autoconfigure.tools;

import com.callibrity.mocapi.autoconfigure.MocapiAutoConfiguration;
import com.callibrity.mocapi.tools.McpTool;
import com.callibrity.mocapi.tools.McpToolProvider;
import com.callibrity.mocapi.tools.McpToolsCapability;
import com.callibrity.mocapi.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.annotation.DefaultAnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@AutoConfiguration
@AutoConfigureBefore(MocapiAutoConfiguration.class)
@ConditionalOnClass(McpToolsCapability.class)
@EnableConfigurationProperties(MocapiToolsProperties.class)
@PropertySource("classpath:mocapi-tools-defaults.properties")
@RequiredArgsConstructor
public class MocapiToolsAutoConfiguration {

// -------------------------- OTHER METHODS --------------------------

    private final MocapiToolsProperties props;

    @Bean
    public McpToolsCapability mcpToolsCapability(List<McpToolProvider> toolProviders) {
        return new McpToolsCapability(toolProviders);
    }

    @Bean
    public McpToolProvider mcpToolBeansProvider(List<McpTool> beans) {
        return () -> List.copyOf(beans);
    }

    @Bean
    MethodSchemaGenerator methodSchemaGenerator(ObjectMapper mapper) {
        return new DefaultMethodSchemaGenerator(mapper, props.getSchemaVersion());
    }

    @Bean
    public AnnotationMcpToolProviderFactory annotationMcpToolProviderFactory(ObjectMapper mapper, MethodSchemaGenerator generator) {
        return new DefaultAnnotationMcpToolProviderFactory(mapper, generator);
    }

    @Bean
    public ToolServiceMcpToolProvider toolServiceMcpToolProvider(ApplicationContext context, ObjectMapper mapper, MethodSchemaGenerator generator) {
        return new ToolServiceMcpToolProvider(context, mapper, generator);
    }
}
