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
package com.callibrity.mocapi.autoconfigure.prompts;

import com.callibrity.mocapi.autoconfigure.MocapiAutoConfiguration;
import com.callibrity.mocapi.prompts.McpPrompt;
import com.callibrity.mocapi.prompts.McpPromptProvider;
import com.callibrity.mocapi.prompts.McpPromptsCapability;
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
@ConditionalOnClass(McpPromptsCapability.class)
@EnableConfigurationProperties(MocapiPromptsProperties.class)
@PropertySource("classpath:mocapi-prompts-defaults.properties")
@RequiredArgsConstructor
public class MocapiPromptsAutoConfiguration {

// ------------------------------ FIELDS ------------------------------

    private final MocapiPromptsProperties props;

// -------------------------- OTHER METHODS --------------------------

    @Bean
    public McpPromptProvider mcpPromptBeanProvider(List<McpPrompt> beans) {
        return () -> List.copyOf(beans);
    }

    @Bean
    public McpPromptsCapability mcpPromptsCapability(List<McpPromptProvider> providers) {
        return new McpPromptsCapability(providers);
    }

    @Bean
    public PromptServiceMcpPromptProvider promptServiceMcpPromptProvider(ApplicationContext context) {
        return new PromptServiceMcpPromptProvider(context);
    }

}
