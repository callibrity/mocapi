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

import com.callibrity.mocapi.autoconfigure.http.McpRequestValidator;
import com.callibrity.mocapi.autoconfigure.http.StreamableHttpController;
import com.callibrity.mocapi.autoconfigure.session.InMemoryMcpSessionStore;
import com.callibrity.mocapi.autoconfigure.session.McpLoggingMethods;
import com.callibrity.mocapi.autoconfigure.session.McpSessionIdParamResolver;
import com.callibrity.mocapi.autoconfigure.session.McpSessionMethods;
import com.callibrity.mocapi.autoconfigure.stream.McpStreamContextParamResolver;
import com.callibrity.mocapi.autoconfigure.tools.McpToolMethods;
import com.callibrity.mocapi.server.InitializeResponse;
import com.callibrity.mocapi.server.LoggingCapabilityDescriptor;
import com.callibrity.mocapi.server.ServerCapabilities;
import com.callibrity.mocapi.server.ServerInfo;
import com.callibrity.mocapi.server.ToolsCapabilityDescriptor;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.mocapi.tools.ToolsRegistry;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
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

  private final MocapiProperties props;

  @Bean
  @ConditionalOnMissingBean
  public InitializeResponse initializeResponse(
      @Nullable ToolsRegistry toolsRegistry, @Nullable BuildProperties buildProperties) {
    String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
    ToolsCapabilityDescriptor tools =
        toolsRegistry != null ? new ToolsCapabilityDescriptor(false) : null;
    return new InitializeResponse(
        InitializeResponse.PROTOCOL_VERSION,
        new ServerCapabilities(tools, new LoggingCapabilityDescriptor()),
        new ServerInfo(props.getServerName(), props.getServerTitle(), version, null, null, null),
        props.getInstructions());
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(McpSessionStore.class)
  public InMemoryMcpSessionStore mcpSessionStore() {
    return new InMemoryMcpSessionStore();
  }

  @Bean
  @ConditionalOnMissingBean
  public McpSessionService mcpSessionService(McpSessionStore store) {
    byte[] masterKey = Base64.getDecoder().decode(props.getSessionEncryptionMasterKey());
    return new McpSessionService(store, masterKey, props.getSessionTimeout());
  }

  @Bean
  @ConditionalOnMissingBean
  public McpRequestValidator mcpRequestValidator() {
    return new McpRequestValidator(props.getAllowedOrigins());
  }

  @Bean
  @ConditionalOnMissingBean
  public McpStreamContextParamResolver mcpStreamContextParamResolver() {
    return new McpStreamContextParamResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public McpSessionIdParamResolver mcpSessionIdParamResolver() {
    return new McpSessionIdParamResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public McpSessionMethods mcpSessionMethods(InitializeResponse initializeResponse) {
    return new McpSessionMethods(initializeResponse);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpLoggingMethods mcpLoggingMethods(McpSessionService sessionService) {
    return new McpLoggingMethods(sessionService);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ToolsRegistry.class)
  public McpToolMethods mcpToolMethods(ToolsRegistry toolsRegistry, ObjectMapper objectMapper) {
    return new McpToolMethods(toolsRegistry, objectMapper);
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
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper,
      McpStreamContextParamResolver streamContextResolver,
      McpSessionIdParamResolver sessionIdResolver,
      MailboxFactory mailboxFactory,
      SchemaGenerator schemaGenerator) {
    return new StreamableHttpController(
        dispatcher,
        mcpRequestValidator,
        sessionService,
        registry,
        objectMapper,
        streamContextResolver,
        sessionIdResolver,
        mailboxFactory,
        schemaGenerator,
        props.getElicitation().getTimeout());
  }
}
