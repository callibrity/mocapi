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
package com.callibrity.mocapi.transport.http;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerProperties;
import com.callibrity.mocapi.server.routing.McpRoutedParamContributor;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Auto-configuration for the stateless Streamable HTTP transport (MCP 2026-07-28). */
@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@ConditionalOnClass(StreamableHttpController.class)
@ConditionalOnBean(McpServer.class)
@EnableConfigurationProperties(MocapiServerProperties.class)
@RequiredArgsConstructor
public class StreamableHttpAutoConfiguration {

  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean(McpRequestValidator.class)
  public McpRequestValidator mcpProtocolRequestValidator() {
    return new McpRequestValidator(props.allowedOrigins());
  }

  @Bean
  @ConditionalOnMissingBean(McpHeaderValidator.class)
  public McpHeaderValidator mcpProtocolHeaderValidator(
      @Autowired(required = false) List<McpRoutedParamContributor> contributors) {
    Map<String, String> additionalNamedParamFields = new HashMap<>();
    if (contributors != null) {
      for (McpRoutedParamContributor contributor : contributors) {
        additionalNamedParamFields.putAll(contributor.namedParamFields());
      }
    }
    return new McpHeaderValidator(additionalNamedParamFields);
  }

  @Bean
  @ConditionalOnMissingBean
  public ContextSnapshotFactory mcpContextSnapshotFactory() {
    return ContextSnapshotFactory.builder().build();
  }

  @Bean
  @ConditionalOnMissingBean(StreamableHttpController.class)
  public StreamableHttpController mcpProtocolStreamableHttpController(
      McpServer protocol,
      McpRequestValidator validator,
      McpHeaderValidator headerValidator,
      ObjectMapper objectMapper,
      ContextSnapshotFactory contextSnapshotFactory) {
    return new StreamableHttpController(
        protocol,
        validator,
        headerValidator,
        objectMapper,
        contextSnapshotFactory,
        props.streamTimeoutOrDefault().toMillis());
  }
}
