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
import com.callibrity.mocapi.prompts.McpPromptsCapability;
import com.callibrity.ripcurl.autoconfigure.RipCurlAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MocapiPromptsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MocapiPromptsAutoConfiguration.class, MocapiAutoConfiguration.class, RipCurlAutoConfiguration.class, JacksonAutoConfiguration.class));

    @Test
    void mcpCapabilityInitializes() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(McpPromptsCapability.class));
    }

    @Test
    void promptServiceMcpPromptProviderInitializes() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PromptServiceMcpPromptProvider.class);
            PromptServiceMcpPromptProvider bean = context.getBean(PromptServiceMcpPromptProvider.class);
            var tools = bean.getMcpPrompts();
            assertThat(tools).isNotNull();
            assertThat(tools).isEmpty();
        });
    }

    @Test
    void mcpPromptBeanProviderInitializes() {
        contextRunner.run(context -> assertThat(context).hasBean("mcpPromptBeanProvider"));
    }
}