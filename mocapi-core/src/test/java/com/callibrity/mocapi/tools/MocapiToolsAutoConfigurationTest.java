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
package com.callibrity.mocapi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.ripcurl.autoconfigure.RipCurlAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.methodical.autoconfigure.MethodicalAutoConfiguration;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class MocapiToolsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "mocapi.session-encryption-master-key=dGhpcy1pcy1hLTMyLWJ5dGUtZGV2LW9ubHkta2V5ISE=")
          .withUserConfiguration(CodecFactoryConfiguration.class)
          .withConfiguration(
              AutoConfigurations.of(
                  MocapiToolsAutoConfiguration.class,
                  MocapiAutoConfiguration.class,
                  RipCurlAutoConfiguration.class,
                  MethodicalAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  SubstrateAutoConfiguration.class,
                  OdysseyAutoConfiguration.class));

  @Configuration(proxyBeanMethods = false)
  static class CodecFactoryConfiguration {
    @Bean
    CodecFactory codecFactory(ObjectMapper objectMapper) {
      return new JacksonCodecFactory(objectMapper);
    }
  }

  @Test
  void mcpCapabilityInitializes() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(ToolsRegistry.class));
  }

  @Test
  void annotationMcpToolProviderFactoryInitializes() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(AnnotationMcpToolProviderFactory.class));
  }

  @Test
  void toolServiceMcpToolProviderInitializes() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ToolServiceMcpToolProvider.class);
          ToolServiceMcpToolProvider bean = context.getBean(ToolServiceMcpToolProvider.class);
          var tools = bean.getMcpTools();
          assertThat(tools).isNotNull();
          assertThat(tools).isEmpty();
        });
  }

  @Test
  void mcpToolBeanProviderInitializes() {
    contextRunner.run(context -> assertThat(context).hasBean("mcpToolBeansProvider"));
  }
}
