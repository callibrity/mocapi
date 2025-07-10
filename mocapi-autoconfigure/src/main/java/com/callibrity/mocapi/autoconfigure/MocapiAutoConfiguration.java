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
package com.callibrity.mocapi.autoconfigure;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpServerCapability;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(MocapiProperties.class)
@PropertySource("classpath:mocapi-defaults.properties")
@RequiredArgsConstructor
public class MocapiAutoConfiguration {

// ------------------------------ FIELDS ------------------------------

    private final MocapiProperties props;

// -------------------------- OTHER METHODS --------------------------

    @Bean
    public McpServer mcpServer(List<McpServerCapability> serverCapabilities) {
        return new McpServer(serverCapabilities, props.getServerInfo(), props.getInstructions());
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
