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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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
                  OAuth2ResourceServerAutoConfiguration.class,
                  MocapiOAuth2AutoConfiguration.class));

  @Test
  void startup_fails_when_no_jwt_decoder_is_registered() {
    // No JwtDecoder-inducing property set, and no @Bean JwtDecoder provided — Spring Boot won't
    // register a decoder, which the MCP spec treats as an unprotected endpoint. The validator
    // must stop the app from starting rather than let it serve /mcp without auth.
    runner
        .withPropertyValues(
            "mocapi.oauth2.resource=https://mcp.example.com",
            "spring.security.oauth2.resourceserver.jwt.audiences=mcp-test")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no JwtDecoder bean"));
  }

  @Test
  void startup_fails_when_mocapi_oauth2_resource_is_not_configured() {
    // mocapi.oauth2.resource is @NotBlank per RFC 9728 §2 (the metadata document's 'resource'
    // field is REQUIRED and has no safe default since it depends on the deployed host).
    // Spring Boot's property binder runs the validation when MocapiOAuth2Properties is
    // @Validated, so an unset property fails the bind at startup rather than emitting a
    // malformed metadata document.
    runner
        .withUserConfiguration(StubJwtDecoderConfig.class)
        .withPropertyValues(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.com",
            "spring.security.oauth2.resourceserver.jwt.audiences[0]=mcp-test")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("resource"));
  }

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
