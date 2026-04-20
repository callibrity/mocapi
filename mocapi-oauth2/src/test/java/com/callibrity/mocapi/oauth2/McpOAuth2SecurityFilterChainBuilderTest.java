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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Implementation;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.DefaultSecurityFilterChain;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpOAuth2SecurityFilterChainBuilderTest {

  private static final String MCP_ENDPOINT = "/mcp";
  private static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

  @Test
  void build_invokes_http_security_pipeline_and_returns_filter_chain() throws Exception {
    HttpSecurity http = mock(HttpSecurity.class, Answers.RETURNS_DEEP_STUBS);
    DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
    when(http.build()).thenReturn(chain);

    MocapiOAuth2Properties properties =
        new MocapiOAuth2Properties(
            "https://api.example.com", List.of(), List.of(), null, null, null);
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    Implementation impl = new Implementation("mocapi", "Mocapi", "1.0.0");

    McpOAuth2SecurityFilterChainBuilder builder =
        new McpOAuth2SecurityFilterChainBuilder(
            properties,
            jwtDecoder,
            null,
            List.of("https://api.example.com"),
            "https://issuer.example.com",
            impl,
            List.of(),
            List.of(),
            MCP_ENDPOINT,
            METADATA_PATH);

    Object result = builder.build(http);

    assertThat(result).isSameAs(chain);
    verify(http).securityMatcher(MCP_ENDPOINT + "/**", METADATA_PATH);
    verify(http).build();
  }

  @Test
  void build_invokes_chain_customizers() throws Exception {
    HttpSecurity http = mock(HttpSecurity.class, Answers.RETURNS_DEEP_STUBS);
    when(http.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

    MocapiOAuth2Properties properties =
        new MocapiOAuth2Properties(
            "https://api.example.com", List.of(), List.of(), null, null, null);
    MocapiOAuth2SecurityFilterChainCustomizer chainCustomizer =
        mock(MocapiOAuth2SecurityFilterChainCustomizer.class);

    McpOAuth2SecurityFilterChainBuilder builder =
        new McpOAuth2SecurityFilterChainBuilder(
            properties,
            mock(JwtDecoder.class),
            null,
            List.of("https://api.example.com"),
            null,
            null,
            List.of(),
            List.of(chainCustomizer),
            MCP_ENDPOINT,
            METADATA_PATH);

    builder.build(http);

    verify(chainCustomizer).customize(any(HttpSecurity.class));
  }
}
