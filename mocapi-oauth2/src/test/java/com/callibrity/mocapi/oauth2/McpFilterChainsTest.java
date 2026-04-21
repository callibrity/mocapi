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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.oauth2.metadata.McpMetadataCustomizer;
import com.callibrity.mocapi.oauth2.token.McpTokenStrategy;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpFilterChainsTest {

  @Test
  void createMcpFilterChain_matches_endpoint_and_wildcard_subpath_and_invokes_chain_customizers()
      throws Exception {
    HttpSecurity http = mock(HttpSecurity.class, Answers.RETURNS_DEEP_STUBS);
    DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
    when(http.build()).thenReturn(chain);

    McpTokenStrategy tokenStrategy = mock(McpTokenStrategy.class);
    McpFilterChainCustomizer chainCustomizer = mock(McpFilterChainCustomizer.class);
    McpFilterChainConfig config =
        new McpFilterChainConfig(tokenStrategy, "/mcp", List.of(chainCustomizer));

    SecurityFilterChain result = McpFilterChains.createMcpFilterChain(http, config);

    assertThat(result).isSameAs(chain);
    verify(http).securityMatcher("/mcp", "/mcp/**");
    verify(chainCustomizer).customize(http);
    verify(http).build();
  }

  @Test
  void createMcpMetadataFilterChain_matches_metadata_path_and_invokes_chain_customizers()
      throws Exception {
    HttpSecurity http = mock(HttpSecurity.class, Answers.RETURNS_DEEP_STUBS);
    DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
    when(http.build()).thenReturn(chain);

    McpTokenStrategy tokenStrategy = mock(McpTokenStrategy.class);
    McpMetadataCustomizer metadataCustomizer = mock(McpMetadataCustomizer.class);
    McpMetadataFilterChainCustomizer chainCustomizer = mock(McpMetadataFilterChainCustomizer.class);
    McpMetadataFilterChainConfig config =
        new McpMetadataFilterChainConfig(
            tokenStrategy, List.of(metadataCustomizer), List.of(chainCustomizer));

    SecurityFilterChain result = McpFilterChains.createMcpMetadataFilterChain(http, config);

    assertThat(result).isSameAs(chain);
    verify(http).securityMatcher(McpFilterChains.METADATA_PATH);
    verify(chainCustomizer).customize(http);
    verify(http).build();
  }
}
