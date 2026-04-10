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
package com.callibrity.mocapi;

import com.callibrity.mocapi.http.McpRequestValidator;
import com.callibrity.mocapi.http.StreamableHttpController;
import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.model.LoggingCapability;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.prompts.McpPromptMethods;
import com.callibrity.mocapi.prompts.PromptsRegistry;
import com.callibrity.mocapi.resources.McpResourceMethods;
import com.callibrity.mocapi.resources.ResourcesRegistry;
import com.callibrity.mocapi.server.McpCompletionMethods;
import com.callibrity.mocapi.session.InMemoryMcpSessionStore;
import com.callibrity.mocapi.session.McpLoggingMethods;
import com.callibrity.mocapi.session.McpSessionMethods;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.mocapi.tools.McpToolMethods;
import com.callibrity.mocapi.tools.ToolsRegistry;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.lang.Nullable;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = ProjectInfoAutoConfiguration.class)
@EnableConfigurationProperties(MocapiProperties.class)
@PropertySource("classpath:mocapi-defaults.properties")
@RequiredArgsConstructor
public class MocapiAutoConfiguration {

  private static final Log log = LogFactory.getLog(MocapiAutoConfiguration.class);

  private final MocapiProperties props;

  @Bean
  @ConditionalOnMissingBean
  public InitializeResult initializeResult(
      ToolsRegistry toolsRegistry,
      ResourcesRegistry resourcesRegistry,
      PromptsRegistry promptsRegistry,
      @Nullable BuildProperties buildProperties) {
    String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
    ToolsCapability tools = toolsRegistry.isEmpty() ? null : new ToolsCapability(false);
    ResourcesCapability resources =
        resourcesRegistry.isEmpty() ? null : new ResourcesCapability(false, false);
    PromptsCapability prompts = promptsRegistry.isEmpty() ? null : new PromptsCapability(false);
    return new InitializeResult(
        InitializeResult.PROTOCOL_VERSION,
        new ServerCapabilities(
            tools, new LoggingCapability(), new CompletionsCapability(), resources, prompts),
        new Implementation(props.getServerName(), props.getServerTitle(), version),
        props.getInstructions());
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolsRegistry toolsRegistry(ObjectMapper objectMapper) {
    return new ToolsRegistry(List.of(), objectMapper, props.getPagination().getPageSize());
  }

  @Bean
  @ConditionalOnMissingBean
  public ResourcesRegistry resourcesRegistry() {
    return new ResourcesRegistry(List.of(), List.of(), props.getPagination().getPageSize());
  }

  @Bean
  @ConditionalOnMissingBean
  public PromptsRegistry promptsRegistry() {
    return new PromptsRegistry(List.of(), props.getPagination().getPageSize());
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(McpSessionStore.class)
  public InMemoryMcpSessionStore mcpSessionStore() {
    log.warn(
        "No McpSessionStore implementation found; using in-memory fallback (single-node only). "
            + "For clustered deployments, provide a McpSessionStore bean.");
    return new InMemoryMcpSessionStore();
  }

  @Bean
  @ConditionalOnMissingBean
  public McpSessionService mcpSessionService(
      McpSessionStore store, OdysseyStreamRegistry streamRegistry) {
    byte[] masterKey = Base64.getDecoder().decode(props.getSessionEncryptionMasterKey());
    return new McpSessionService(store, masterKey, props.getSessionTimeout(), streamRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpRequestValidator mcpRequestValidator() {
    return new McpRequestValidator(props.getAllowedOrigins());
  }

  @Bean
  @ConditionalOnMissingBean
  public McpSessionMethods mcpSessionMethods(InitializeResult initializeResult) {
    return new McpSessionMethods(initializeResult);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpLoggingMethods mcpLoggingMethods(McpSessionService sessionService) {
    return new McpLoggingMethods(sessionService);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpCompletionMethods mcpCompletionMethods() {
    return new McpCompletionMethods();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ResourcesRegistry.class)
  public McpResourceMethods mcpResourceMethods(ResourcesRegistry resourcesRegistry) {
    return new McpResourceMethods(resourcesRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(PromptsRegistry.class)
  public McpPromptMethods mcpPromptMethods(PromptsRegistry promptsRegistry) {
    return new McpPromptMethods(promptsRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ToolsRegistry.class)
  public McpToolMethods mcpToolMethods(
      ToolsRegistry toolsRegistry,
      ObjectMapper objectMapper,
      MailboxFactory mailboxFactory,
      SchemaGenerator schemaGenerator,
      McpSessionService sessionService) {
    return new McpToolMethods(
        toolsRegistry,
        objectMapper,
        mailboxFactory,
        schemaGenerator,
        sessionService,
        props.getElicitation().getTimeout());
  }

  @Bean
  @ConditionalOnMissingBean(SchemaGenerator.class)
  public SchemaGenerator elicitationSchemaGenerator(ObjectMapper mapper) {
    return new SchemaGenerator(
        new SchemaGeneratorConfigBuilder(
                mapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
            .with(new JacksonSchemaModule())
            .with(new JakartaValidationModule())
            .build());
  }

  @Bean
  @ConditionalOnMissingBean
  public StreamableHttpController mcpStreamingController(
      JsonRpcDispatcher dispatcher,
      McpRequestValidator mcpRequestValidator,
      McpSessionService sessionService,
      ObjectMapper objectMapper,
      MailboxFactory mailboxFactory) {
    return new StreamableHttpController(
        dispatcher, mcpRequestValidator, sessionService, objectMapper, mailboxFactory);
  }
}
