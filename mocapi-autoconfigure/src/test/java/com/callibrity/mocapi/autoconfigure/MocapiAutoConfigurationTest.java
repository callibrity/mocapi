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
import com.callibrity.ripcurl.autoconfigure.RipCurlAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MocapiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MocapiAutoConfiguration.class, RipCurlAutoConfiguration.class, JacksonAutoConfiguration.class));


    @Test
    void mcpServerBeanInitializes() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpServer.class);
        });
    }

    @Test
    void jsonRpcMethodHandlerInitializes() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("mcpServerMethodHandlerProvider");
        });
    }
}