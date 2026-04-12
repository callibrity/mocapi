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
package com.callibrity.mocapi.protocol.autoconfigure;

import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.model.LoggingCapability;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.protocol.DefaultMcpProtocol;
import com.callibrity.mocapi.protocol.McpProtocol;
import com.callibrity.mocapi.protocol.McpResponseCorrelationService;
import com.callibrity.mocapi.protocol.completions.McpCompletionsService;
import com.callibrity.mocapi.protocol.logging.McpLoggingService;
import com.callibrity.mocapi.protocol.ping.McpPingService;
import com.callibrity.mocapi.protocol.prompts.McpPromptProvider;
import com.callibrity.mocapi.protocol.prompts.McpPromptsService;
import com.callibrity.mocapi.protocol.resources.McpResourceProvider;
import com.callibrity.mocapi.protocol.resources.McpResourceTemplateProvider;
import com.callibrity.mocapi.protocol.resources.McpResourcesService;
import com.callibrity.mocapi.protocol.session.McpSessionService;
import com.callibrity.mocapi.protocol.session.McpSessionStore;
import com.callibrity.mocapi.protocol.tools.McpToolsService;
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
@EnableConfigurationProperties(MocapiProtocolProperties.class)
@PropertySource("classpath:mocapi-protocol-defaults.properties")
@RequiredArgsConstructor
public class MocapiProtocolAutoConfiguration {

  private final MocapiProtocolProperties props;

  @Bean
  @ConditionalOnMissingBean
  public InitializeResult mcpProtocolInitializeResult(
      McpToolsService toolsService,
      McpResourcesService resourcesService,
      McpPromptsService promptsService,
      @Nullable BuildProperties buildProperties) {
    String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
    ToolsCapability tools = toolsService.isEmpty() ? null : new ToolsCapability(false);
    ResourcesCapability resources =
        resourcesService.isEmpty() ? null : new ResourcesCapability(false, false);
    PromptsCapability prompts = promptsService.isEmpty() ? null : new PromptsCapability(false);
    return new InitializeResult(
        InitializeResult.PROTOCOL_VERSION,
        new ServerCapabilities(
            tools, new LoggingCapability(), new CompletionsCapability(), resources, prompts),
        new Implementation(props.getServerName(), props.getServerTitle(), version),
        props.getInstructions());
  }

  @Bean
  @ConditionalOnMissingBean(McpSessionStore.class)
  public McpSessionStore mcpProtocolSessionStore(AtomFactory atomFactory) {
    return new SubstrateAtomMcpSessionStore(atomFactory, props.getSessionTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(McpSessionService.class)
  public McpSessionService mcpProtocolSessionService(McpSessionStore store) {
    return new McpSessionService(store, props.getSessionTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(McpResponseCorrelationService.class)
  public McpResponseCorrelationService mcpProtocolCorrelationService(
      MailboxFactory mailboxFactory, ObjectMapper objectMapper) {
    return new McpResponseCorrelationService(
        mailboxFactory, objectMapper, props.getElicitation().getTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(McpProtocol.class)
  public DefaultMcpProtocol mcpProtocol(
      McpSessionService sessionService,
      InitializeResult initializeResult,
      ObjectMapper objectMapper,
      JsonRpcDispatcher dispatcher,
      McpResponseCorrelationService correlationService) {
    return new DefaultMcpProtocol(
        sessionService, initializeResult, objectMapper, dispatcher, correlationService);
  }

  @Bean
  @ConditionalOnMissingBean(McpPromptsService.class)
  public McpPromptsService mcpProtocolPromptsService(List<McpPromptProvider> promptProviders) {
    return new McpPromptsService(promptProviders, props.getPagination().getPageSize());
  }

  @Bean
  @ConditionalOnMissingBean(McpResourcesService.class)
  public McpResourcesService mcpProtocolResourcesService(
      List<McpResourceProvider> resourceProviders,
      List<McpResourceTemplateProvider> templateProviders) {
    return new McpResourcesService(
        resourceProviders, templateProviders, props.getPagination().getPageSize());
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
  @ConditionalOnMissingBean(McpLoggingService.class)
  public McpLoggingService mcpProtocolLoggingService(McpSessionService sessionService) {
    return new McpLoggingService(sessionService);
  }
}
