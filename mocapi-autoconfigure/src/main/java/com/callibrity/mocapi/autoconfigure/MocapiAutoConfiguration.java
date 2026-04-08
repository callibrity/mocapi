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

import com.callibrity.mocapi.autoconfigure.http.InMemoryMcpSessionStore;
import com.callibrity.mocapi.autoconfigure.http.McpStreamContextParamResolver;
import com.callibrity.mocapi.autoconfigure.http.StreamableHttpController;
import com.callibrity.mocapi.autoconfigure.tools.McpToolMethods;
import com.callibrity.mocapi.server.McpRequestValidator;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpServerCapability;
import com.callibrity.mocapi.server.McpSessionStore;
import com.callibrity.mocapi.server.util.McpEventIdCodec;
import com.callibrity.mocapi.tools.McpToolsCapability;
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
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableConfigurationProperties(MocapiProperties.class)
@PropertySource("classpath:mocapi-defaults.properties")
@RequiredArgsConstructor
public class MocapiAutoConfiguration {

  private final MocapiProperties props;

  @Bean
  public McpServer mcpServer(List<McpServerCapability> serverCapabilities) {
    return new McpServer(serverCapabilities, props.getServerInfo(), props.getInstructions());
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(McpSessionStore.class)
  public InMemoryMcpSessionStore mcpSessionStore() {
    return new InMemoryMcpSessionStore();
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
  public McpServerMethods mcpServerMethods(McpServer mcpServer) {
    return new McpServerMethods(mcpServer);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(McpToolsCapability.class)
  public McpToolMethods mcpToolMethods(
      McpToolsCapability toolsCapability, ObjectMapper objectMapper) {
    return new McpToolMethods(toolsCapability, objectMapper);
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
  @ConditionalOnProperty("mocapi.event-id.master-key")
  public McpEventIdCodec mcpEventIdCodec() {
    String masterKeyBase64 = props.getEventId().getMasterKey();
    byte[] masterKeyBytes = Base64.getDecoder().decode(masterKeyBase64);
    return new McpEventIdCodec(masterKeyBytes);
  }

  @Bean
  @ConditionalOnMissingBean
  public StreamableHttpController mcpStreamingController(
      JsonRpcDispatcher dispatcher,
      McpRequestValidator mcpRequestValidator,
      McpSessionStore sessionStore,
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper,
      McpStreamContextParamResolver streamContextResolver,
      MailboxFactory mailboxFactory,
      SchemaGenerator schemaGenerator) {
    return new StreamableHttpController(
        dispatcher,
        mcpRequestValidator,
        sessionStore,
        registry,
        objectMapper,
        streamContextResolver,
        props.getSessionTimeout(),
        mailboxFactory,
        schemaGenerator,
        props.getElicitation().getTimeout());
  }
}
