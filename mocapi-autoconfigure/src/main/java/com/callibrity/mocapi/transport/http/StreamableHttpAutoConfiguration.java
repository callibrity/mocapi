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
import com.callibrity.mocapi.server.routing.RoutedParamContributor;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

  /**
   * Merges every {@link RoutedParamContributor} bean into the {@code Mcp-Name} routing-header
   * table, failing the boot with an {@link IllegalStateException} naming both parties when two
   * contributors claim the same JSON-RPC method, or when a contribution collides with a method
   * mocapi's built-in table already covers (mirrors {@code ResourceContributor}'s duplicate-URI
   * treatment). Beans are injected by name (rather than {@code List}) purely so the failure message
   * can name the offending beans.
   */
  @Bean
  @ConditionalOnMissingBean(McpHeaderValidator.class)
  public McpHeaderValidator mcpProtocolHeaderValidator(
      @Autowired(required = false) Map<String, RoutedParamContributor> contributors) {
    Map<String, String> additionalNamedParamFields = new HashMap<>();
    if (contributors != null) {
      Set<String> builtIns = McpHeaderValidator.builtInNamedParamMethods();
      Map<String, String> contributedBy = new HashMap<>();
      for (Map.Entry<String, RoutedParamContributor> contributorEntry : contributors.entrySet()) {
        String beanName = contributorEntry.getKey();
        for (Map.Entry<String, String> entry :
            contributorEntry.getValue().namedParamFields().entrySet()) {
          String method = entry.getKey();
          if (builtIns.contains(method)) {
            throw new IllegalStateException(
                "RoutedParamContributor \""
                    + beanName
                    + "\" contributes method \""
                    + method
                    + "\", which collides with mocapi's built-in Mcp-Name routing table");
          }
          String existingBeanName = contributedBy.putIfAbsent(method, beanName);
          if (existingBeanName != null) {
            throw new IllegalStateException(
                "RoutedParamContributor \""
                    + existingBeanName
                    + "\" and \""
                    + beanName
                    + "\" both contribute method \""
                    + method
                    + "\" to the Mcp-Name routing table");
          }
          additionalNamedParamFields.put(method, entry.getValue());
        }
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
