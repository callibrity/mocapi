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
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 * Autoconfig for mocapi-oauth2: fail-fast checks plus {@link McpOAuth2SecurityFilterChainBuilder}.
 */
@AutoConfiguration(after = OAuth2ResourceServerAutoConfiguration.class)
@ConditionalOnClass({OAuth2ProtectedResourceMetadataCustomizer.class, HttpSecurity.class})
@EnableConfigurationProperties(MocapiOAuth2Properties.class)
@PropertySource("classpath:mocapi-oauth2-defaults.properties")
public class MocapiOAuth2AutoConfiguration {

  /** RFC 9728 §3 well-known path; Spring Security's metadata filter hardcodes the same. */
  public static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

  public MocapiOAuth2AutoConfiguration(
      MocapiOAuth2Properties properties,
      ObjectProvider<JwtDecoder> jwtDecoder,
      ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospector,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties) {
    Objects.requireNonNull(properties);
    var audiences = audiencesFrom(springResourceServerProperties);
    MocapiOAuth2Compliance.validate(
        jwtDecoder.getIfAvailable(), opaqueTokenIntrospector.getIfAvailable(), audiences);
    MocapiOAuth2ResourceResolver.resolve(properties, audiences);
  }

  @Bean
  @ConditionalOnMissingBean(name = "mcpOAuth2SecurityFilterChain")
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain mcpOAuth2SecurityFilterChain(
      HttpSecurity http,
      MocapiOAuth2Properties properties,
      ObjectProvider<JwtDecoder> jwtDecoder,
      ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospector,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties,
      ObjectProvider<Implementation> mcpServerInfo,
      ObjectProvider<OAuth2ProtectedResourceMetadataCustomizer> metadataCustomizers,
      ObjectProvider<MocapiOAuth2SecurityFilterChainCustomizer> chainCustomizers,
      @Value("${mocapi.endpoint:/mcp}") String mcpEndpoint)
      throws Exception {
    return new McpOAuth2SecurityFilterChainBuilder(
            properties,
            jwtDecoder.getIfAvailable(),
            opaqueTokenIntrospector.getIfAvailable(),
            audiencesFrom(springResourceServerProperties),
            springIssuerUriFrom(springResourceServerProperties),
            mcpServerInfo.getIfAvailable(),
            metadataCustomizers.orderedStream().toList(),
            chainCustomizers.orderedStream().toList(),
            mcpEndpoint,
            METADATA_PATH)
        .build(http);
  }

  private static List<String> audiencesFrom(ObjectProvider<OAuth2ResourceServerProperties> p) {
    var rs = p.getIfAvailable();
    return (rs == null) ? List.of() : rs.getJwt().getAudiences();
  }

  private static String springIssuerUriFrom(ObjectProvider<OAuth2ResourceServerProperties> p) {
    var rs = p.getIfAvailable();
    return (rs == null) ? null : rs.getJwt().getIssuerUri();
  }
}
