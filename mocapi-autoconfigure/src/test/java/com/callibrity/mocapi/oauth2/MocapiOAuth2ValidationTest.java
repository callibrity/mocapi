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
package com.callibrity.mocapi.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.ClassUtils;

/**
 * MCP compliance guardrails — the auto-configuration fails at startup when either prerequisite for
 * spec-compliant operation is missing. Uses {@link ApplicationContextRunner} rather than
 * {@code @SpringBootTest} so the expected-failure paths are cleanly assertable without
 * bootstrapping a servlet environment.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2ValidationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  oauth2ResourceServerAutoConfiguration(), MocapiOAuth2AutoConfiguration.class));

  /**
   * Boot 4.1 relocated {@code OAuth2ResourceServerAutoConfiguration} (the {@code servlet} package
   * segment was dropped), so the class is resolved by name from its known homes — mirroring the
   * dual-location {@code afterName} reference in {@link MocapiOAuth2AutoConfiguration} — to keep
   * this test compiling and meaningful on both the 4.0 and 4.1 lines (the {@code boot-next} CI leg
   * runs it against the newest minor).
   */
  private static Class<?> oauth2ResourceServerAutoConfiguration() {
    ClassLoader cl = MocapiOAuth2ValidationTest.class.getClassLoader();
    return List.of(
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration")
        .stream()
        .filter(name -> ClassUtils.isPresent(name, cl))
        .findFirst()
        .map(name -> ClassUtils.resolveClassName(name, cl))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "OAuth2ResourceServerAutoConfiguration not found at any known location — "
                        + "Spring Boot moved it again; update the dual-location lists here and in "
                        + "MocapiOAuth2AutoConfiguration"));
  }

  @Test
  void startup_fails_when_neither_jwt_decoder_nor_opaque_introspector_is_registered() {
    // No JwtDecoder-inducing property set, and no @Bean JwtDecoder provided — Spring Boot won't
    // register a decoder, which the MCP spec treats as an unprotected endpoint. The validator
    // must stop the app from starting rather than let it serve /mcp without auth.
    runner
        .withPropertyValues(
            "mocapi.oauth2.resource=https://mcp.example.com",
            "spring.security.oauth2.resourceserver.jwt.audiences=https://mcp.example.com")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("neither JwtDecoder nor OpaqueTokenIntrospector"));
  }

  // NOTE: there is deliberately no context-level "resource not configured" test here. The
  // RFC 9728 resource resolution/validation contract (explicit-must-be-in-audiences,
  // default-from-sole-audience, ambiguous-fails-fast) lives in ResourceMetadataCustomizer and is
  // covered path-by-path in ResourceMetadataCustomizerTest via direct instantiation — which is
  // deterministic on every Spring Boot line. A context-runner variant here asserted only a
  // bean-instantiation-order race: it passed on Boot 4.0 by coincidentally substring-matching
  // "resource" inside an unrelated missing-bean FQCN, and Boot 4.1's different instantiation
  // order exposed it.

  @Test
  void startup_fails_when_audiences_is_empty() {
    // JwtDecoder is present via a stub bean, but no audiences property is set — Spring's audience
    // validator won't be wired, which the MCP spec treats as a confused-deputy hole.
    runner
        .withUserConfiguration(StubJwtDecoderConfig.class)
        .withPropertyValues(
            "mocapi.oauth2.resource=https://mcp.example.com",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.com")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("audiences"));
  }

  @Configuration(proxyBeanMethods = false)
  static class StubJwtDecoderConfig {
    @Bean
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }
}
