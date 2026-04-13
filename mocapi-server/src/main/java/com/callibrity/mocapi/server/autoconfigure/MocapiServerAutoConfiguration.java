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
package com.callibrity.mocapi.server.autoconfigure;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.DefaultMcpServer;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransportResolver;
import com.callibrity.mocapi.server.ServerCapabilitiesContributor;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.lifecycle.McpLifecycleService;
import com.callibrity.mocapi.server.logging.McpLoggingService;
import com.callibrity.mocapi.server.ping.McpPingService;
import com.callibrity.mocapi.server.prompts.McpPromptProvider;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourceProvider;
import com.callibrity.mocapi.server.resources.McpResourceTemplateProvider;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.session.AtomMcpSessionStore;
import com.callibrity.mocapi.server.session.McpSessionResolver;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.lang.Nullable;
import tools.jackson.databind.ObjectMapper;

/** Auto-configuration for MCP protocol beans. */
@AutoConfiguration(after = {ProjectInfoAutoConfiguration.class, SubstrateAutoConfiguration.class})
@EnableConfigurationProperties(MocapiServerProperties.class)
@PropertySource("classpath:mocapi-server-defaults.properties")
@RequiredArgsConstructor
public class MocapiServerAutoConfiguration {

  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean
  public Implementation mcpServerInfo(@Nullable BuildProperties buildProperties) {
    String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
    return new Implementation(props.serverName(), props.serverTitle(), version);
  }

  @Bean
  @ConditionalOnMissingBean(McpSessionStore.class)
  public McpSessionStore mcpProtocolSessionStore(AtomFactory atomFactory) {
    return new AtomMcpSessionStore(atomFactory, props.sessionTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(McpSessionResolver.class)
  public McpSessionResolver mcpProtocolSessionResolver() {
    return new McpSessionResolver();
  }

  @Bean
  @ConditionalOnMissingBean(McpTransportResolver.class)
  public McpTransportResolver mcpProtocolTransportResolver() {
    return new McpTransportResolver();
  }

  @Bean
  @ConditionalOnMissingBean(McpSessionService.class)
  public McpSessionService mcpProtocolSessionService(
      McpSessionStore store,
      Implementation serverInfo,
      List<ServerCapabilitiesContributor> contributors) {
    return new McpSessionService(
        store, props.sessionTimeout(), serverInfo, props.instructions(), contributors);
  }

  @Bean
  @ConditionalOnMissingBean(McpResponseCorrelationService.class)
  public McpResponseCorrelationService mcpProtocolCorrelationService(
      MailboxFactory mailboxFactory, ObjectMapper objectMapper) {
    return new McpResponseCorrelationService(
        mailboxFactory, objectMapper, props.elicitation().timeout());
  }

  @Bean
  @ConditionalOnMissingBean(McpServer.class)
  public DefaultMcpServer mcpProtocol(
      McpSessionService sessionService,
      JsonRpcDispatcher dispatcher,
      McpResponseCorrelationService correlationService) {
    return new DefaultMcpServer(sessionService, dispatcher, correlationService);
  }

  @Bean
  @ConditionalOnMissingBean(McpPromptsService.class)
  public McpPromptsService mcpProtocolPromptsService(List<McpPromptProvider> promptProviders) {
    return new McpPromptsService(promptProviders, props.pagination().pageSize());
  }

  @Bean
  @ConditionalOnMissingBean(McpResourcesService.class)
  public McpResourcesService mcpProtocolResourcesService(
      List<McpResourceProvider> resourceProviders,
      List<McpResourceTemplateProvider> templateProviders) {
    return new McpResourcesService(
        resourceProviders, templateProviders, props.pagination().pageSize());
  }

  @Bean
  @ConditionalOnMissingBean(McpPingService.class)
  public McpPingService mcpProtocolPingService() {
    return new McpPingService();
  }

  @Bean
  @ConditionalOnMissingBean(McpCompletionsService.class)
  public McpCompletionsService mcpProtocolCompletionsService() {
    return new McpCompletionsService();
  }

  @Bean
  @ConditionalOnMissingBean(McpLifecycleService.class)
  public McpLifecycleService mcpProtocolLifecycleService(McpSessionService sessionService) {
    return new McpLifecycleService(sessionService);
  }

  @Bean
  @ConditionalOnMissingBean(McpLoggingService.class)
  public McpLoggingService mcpProtocolLoggingService(McpSessionService sessionService) {
    return new McpLoggingService(sessionService);
  }
}
