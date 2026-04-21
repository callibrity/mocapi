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
package com.callibrity.mocapi.oauth2;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.oauth2.metadata.AuthorizationServersMetadataCustomizer;
import com.callibrity.mocapi.oauth2.metadata.ClaimsMetadataCustomizer;
import com.callibrity.mocapi.oauth2.metadata.McpMetadataCustomizer;
import com.callibrity.mocapi.oauth2.metadata.ResourceMetadataCustomizer;
import com.callibrity.mocapi.oauth2.metadata.ResourceNameMetadataCustomizer;
import com.callibrity.mocapi.oauth2.metadata.ScopesSupportedMetadataCustomizer;
import com.callibrity.mocapi.oauth2.token.JwtMcpTokenStrategy;
import com.callibrity.mocapi.oauth2.token.McpTokenStrategy;
import com.callibrity.mocapi.oauth2.token.OpaqueTokenMcpTokenStrategy;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autoconfig for mocapi-oauth2. Performs fail-fast validation of the OAuth2 resource-server
 * configuration (via {@link MocapiOAuth2Compliance}), resolves the MCP resource identifier (via
 * {@link MocapiOAuth2ResourceResolver}), and registers:
 *
 * <ul>
 *   <li>A single {@link McpTokenStrategy} bean — either {@link JwtMcpTokenStrategy} (when Spring
 *       Boot has wired a {@code JwtDecoder}) or {@link OpaqueTokenMcpTokenStrategy} (when it has
 *       wired an {@code OpaqueTokenIntrospector}). Exactly one of the two {@code @Bean} methods
 *       activates via mutually-exclusive {@code @ConditionalOnBean} gates.
 *   <li>Five baseline {@link McpMetadataCustomizer} beans that populate the RFC 9728
 *       protected-resource metadata document: {@link ResourceMetadataCustomizer}, {@link
 *       AuthorizationServersMetadataCustomizer}, {@link ScopesSupportedMetadataCustomizer}, {@link
 *       ResourceNameMetadataCustomizer}, and {@link ResourceDocumentationMetadataCustomizer}. Each
 *       is one bean that knows exactly one facet of the metadata document — users who want to
 *       override a specific facet register their own {@link McpMetadataCustomizer} with a
 *       lower-precedence {@code @Order} to overwrite it, or a {@link
 *       org.springframework.context.annotation.Primary @Primary} replacement to take over entirely.
 *   <li>{@code mcpMetadataFilterChain} — serves {@code /.well-known/oauth-protected-resource}
 *       ({@code permitAll}); ordered before the MCP chain so the public metadata path wins.
 *   <li>{@code mcpFilterChain} — enforces OAuth2 bearer-token authentication on the MCP endpoint
 *       via the registered {@link McpTokenStrategy}.
 * </ul>
 */
@AutoConfiguration(after = OAuth2ResourceServerAutoConfiguration.class)
@ConditionalOnClass({McpMetadataCustomizer.class, HttpSecurity.class})
@EnableConfigurationProperties(MocapiOAuth2Properties.class)
@PropertySource("classpath:mocapi-oauth2-defaults.properties")
public class MocapiOAuth2AutoConfiguration {

  public MocapiOAuth2AutoConfiguration(
      MocapiOAuth2Properties properties,
      ObjectProvider<JwtDecoder> jwtDecoder,
      ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospector,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties) {
    Objects.requireNonNull(properties);
    var audiences = audiencesFrom(springResourceServerProperties);
    MocapiOAuth2Compliance.validate(
        jwtDecoder.getIfAvailable(), opaqueTokenIntrospector.getIfAvailable(), audiences);
  }

  @Bean
  @ConditionalOnBean(JwtDecoder.class)
  @ConditionalOnMissingBean(McpTokenStrategy.class)
  public McpTokenStrategy jwtMcpTokenStrategy() {
    return new JwtMcpTokenStrategy();
  }

  @Bean
  @ConditionalOnBean(OpaqueTokenIntrospector.class)
  @ConditionalOnMissingBean(McpTokenStrategy.class)
  public McpTokenStrategy opaqueTokenMcpTokenStrategy(
      OpaqueTokenIntrospector introspector,
      OAuth2ResourceServerProperties resourceServerProperties) {
    return new OpaqueTokenMcpTokenStrategy(
        introspector, resourceServerProperties.getJwt().getAudiences());
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean(ResourceMetadataCustomizer.class)
  public ResourceMetadataCustomizer mocapiResourceMetadataCustomizer(
      MocapiOAuth2Properties properties, OAuth2ResourceServerProperties resourceServerProperties) {
    return new ResourceMetadataCustomizer(properties, resourceServerProperties);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean(AuthorizationServersMetadataCustomizer.class)
  public AuthorizationServersMetadataCustomizer mocapiAuthorizationServersMetadataCustomizer(
      MocapiOAuth2Properties properties, OAuth2ResourceServerProperties resourceServerProperties) {
    return new AuthorizationServersMetadataCustomizer(properties, resourceServerProperties);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean(ScopesSupportedMetadataCustomizer.class)
  public ScopesSupportedMetadataCustomizer mocapiScopesSupportedMetadataCustomizer(
      MocapiOAuth2Properties properties) {
    return new ScopesSupportedMetadataCustomizer(properties);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean(ResourceNameMetadataCustomizer.class)
  public ResourceNameMetadataCustomizer mocapiResourceNameMetadataCustomizer(
      Implementation mcpServerInfo) {
    return new ResourceNameMetadataCustomizer(mcpServerInfo);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnMissingBean(ClaimsMetadataCustomizer.class)
  public ClaimsMetadataCustomizer mocapiClaimsMetadataCustomizer(
      MocapiOAuth2Properties properties) {
    return new ClaimsMetadataCustomizer(properties);
  }

  @Bean
  @ConditionalOnMissingBean(name = "mcpMetadataFilterChain")
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain mcpMetadataFilterChain(
      HttpSecurity http,
      McpTokenStrategy tokenStrategy,
      List<McpMetadataCustomizer> metadataCustomizers,
      List<McpMetadataFilterChainCustomizer> chainCustomizers)
      throws Exception {
    return McpFilterChains.createMcpMetadataFilterChain(
        http,
        new McpMetadataFilterChainConfig(tokenStrategy, metadataCustomizers, chainCustomizers));
  }

  @Bean
  @ConditionalOnMissingBean(name = "mcpFilterChain")
  @Order(Ordered.HIGHEST_PRECEDENCE + 10)
  public SecurityFilterChain mcpFilterChain(
      HttpSecurity http,
      McpTokenStrategy tokenStrategy,
      List<McpFilterChainCustomizer> chainCustomizers,
      @Value("${mocapi.endpoint:/mcp}") String mcpEndpoint)
      throws Exception {
    return McpFilterChains.createMcpFilterChain(
        http, new McpFilterChainConfig(tokenStrategy, mcpEndpoint, chainCustomizers));
  }

  private static List<String> audiencesFrom(ObjectProvider<OAuth2ResourceServerProperties> p) {
    var rs = p.getIfAvailable();
    return (rs == null) ? List.of() : rs.getJwt().getAudiences();
  }
}
