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

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.DefaultMcpServer;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransportResolver;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.discover.DiscoverHandler;
import com.callibrity.mocapi.server.elicitation.ElicitationNotSupportedExceptionTranslator;
import com.callibrity.mocapi.server.exchange.MetaEnvelopeParser;
import com.callibrity.mocapi.server.lifecycle.McpLifecycleService;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.StringValueResolver;
import tools.jackson.databind.ObjectMapper;

/** Auto-configuration for MCP protocol beans (stateless 2026-07-28 model, ADR-0020). */
@AutoConfiguration(after = ProjectInfoAutoConfiguration.class)
@EnableConfigurationProperties(MocapiServerProperties.class)
@PropertySource("classpath:mocapi-server-defaults.properties")
@RequiredArgsConstructor
public class MocapiServerAutoConfiguration {

  private final MocapiServerProperties props;

  /**
   * Exposes Spring's embedded-value resolver as a {@link StringValueResolver} bean so annotation
   * processors (tool, prompt, resource, resource-template scanners) can resolve {@code ${...}}
   * property placeholders and {@code #{...}} SpEL expressions in annotation values. Missing
   * placeholders fail loudly at startup via Spring's standard {@code
   * PlaceholderResolutionException}.
   */
  @Bean
  @ConditionalOnMissingBean(name = "mcpAnnotationValueResolver")
  public StringValueResolver mcpAnnotationValueResolver(ConfigurableBeanFactory beanFactory) {
    return beanFactory::resolveEmbeddedValue;
  }

  @Bean
  @ConditionalOnMissingBean(HandlerMethodsCache.class)
  public HandlerMethodsCache handlerMethodsCache(ConfigurableListableBeanFactory beanFactory) {
    return HandlerMethodsCache.scan(
        beanFactory,
        List.of(McpTool.class, McpPrompt.class, McpResource.class, McpResourceTemplate.class));
  }

  @Bean
  @ConditionalOnMissingBean
  public Implementation mcpServerInfo(@Nullable BuildProperties buildProperties) {
    String version = buildProperties != null ? buildProperties.getVersion() : "unknown";
    return new Implementation(props.serverName(), props.serverTitle(), version, null);
  }

  @Bean
  @ConditionalOnMissingBean(McpTransportResolver.class)
  public McpTransportResolver mcpProtocolTransportResolver() {
    return new McpTransportResolver();
  }

  /**
   * Attaches mocapi's ScopedValue-backed {@link McpTransportResolver} to every ripcurl
   * {@code @JsonRpcMethod} handler. Ripcurl 2.8 removed the blind {@code List<ParameterResolver<?
   * super JsonNode>>} autowiring path; per-handler attachment via a customizer is the replacement
   * contract.
   */
  @Bean
  public com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandlerCustomizer
      mocapiResolverCustomizer(McpTransportResolver transportResolver) {
    return config -> config.resolver(transportResolver);
  }

  @Bean
  @ConditionalOnMissingBean(McpCompletionsService.class)
  public McpCompletionsService mcpCompletionsService() {
    return new McpCompletionsService();
  }

  @Bean
  @ConditionalOnMissingBean(ServerCapabilities.class)
  public ServerCapabilities mcpServerCapabilities() {
    return new ServerCapabilities(
        null,
        new ToolsCapability(false),
        null,
        new CompletionsCapability(),
        new ResourcesCapability(false, false),
        new PromptsCapability(false),
        Map.of());
  }

  @Bean
  @ConditionalOnMissingBean(CacheSettings.class)
  public CacheSettings mcpCacheSettings() {
    var cache = props.cache();
    return cache == null
        ? CacheSettings.defaults()
        : new CacheSettings(cache.listTtl(), cache.readTtl(), cache.scope());
  }

  /**
   * The MRTR requestState codec (ADR-0021). A blank {@code mocapi.mrtr.secret} yields an ephemeral
   * key — the codec factory logs the production warning itself.
   */
  @Bean
  @ConditionalOnMissingBean(RequestStateCodec.class)
  public RequestStateCodec mcpRequestStateCodec(ObjectMapper objectMapper) {
    var mrtr = props.mrtr();
    var ttl = mrtr == null || mrtr.ttl() == null ? RequestStateCodec.DEFAULT_TTL : mrtr.ttl();
    String secret = mrtr == null ? null : mrtr.secret();
    return secret == null || secret.isBlank()
        ? RequestStateCodec.withEphemeralKey(ttl, objectMapper)
        : RequestStateCodec.withSecret(secret, ttl, objectMapper);
  }

  /**
   * Default {@link McpPrincipalSource}: unauthenticated (no principal). An authenticated deployment
   * supplies its own bean — e.g. one reading the OAuth2 JWT subject — to bind {@code requestState}
   * tokens to their caller and reject cross-principal replay.
   */
  @Bean
  @ConditionalOnMissingBean(McpPrincipalSource.class)
  public McpPrincipalSource mcpPrincipalSource() {
    return () -> null;
  }

  @Bean
  @ConditionalOnMissingBean(MrtrElicitationEngine.class)
  public MrtrElicitationEngine mcpElicitationEngine(
      RequestStateCodec codec, ObjectMapper objectMapper, McpPrincipalSource principalSource) {
    return new MrtrElicitationEngine(codec, objectMapper, principalSource);
  }

  /** Maps {@code McpElicitationNotSupportedException} to {@code -32003} on the wire. */
  @Bean
  public ElicitationNotSupportedExceptionTranslator mcpElicitationNotSupportedTranslator(
      ObjectMapper objectMapper) {
    return new ElicitationNotSupportedExceptionTranslator(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(MetaEnvelopeParser.class)
  public MetaEnvelopeParser mcpMetaEnvelopeParser(ObjectMapper objectMapper) {
    return new MetaEnvelopeParser(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(DiscoverHandler.class)
  public DiscoverHandler mcpDiscoverHandler(
      Implementation serverInfo, ServerCapabilities capabilities, CacheSettings cacheSettings) {
    return new DiscoverHandler(serverInfo, props.instructions(), capabilities, cacheSettings);
  }

  @Bean
  @ConditionalOnMissingBean(McpLifecycleService.class)
  public McpLifecycleService mcpProtocolLifecycleService() {
    return new McpLifecycleService();
  }

  @Bean
  @ConditionalOnMissingBean(McpServer.class)
  public DefaultMcpServer mcpProtocol(
      JsonRpcDispatcher dispatcher, MetaEnvelopeParser envelopeParser, ObjectMapper objectMapper) {
    return new DefaultMcpServer(dispatcher, envelopeParser, objectMapper);
  }
}
