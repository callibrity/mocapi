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

import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a Spring Security-backed {@link McpPrincipalSource} so MRTR {@code requestState} tokens
 * bind to the authenticated caller (ADR-0021). Ordered before {@link MocapiServerAutoConfiguration}
 * so this real source wins over the core's unauthenticated default, while a user-supplied {@code
 * McpPrincipalSource} bean still takes precedence over both (via {@link ConditionalOnMissingBean}).
 */
@AutoConfiguration(before = MocapiServerAutoConfiguration.class)
@ConditionalOnClass(SecurityContextMcpPrincipalSource.class)
public class MocapiOAuth2PrincipalSourceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(McpPrincipalSource.class)
  public McpPrincipalSource mcpSecurityContextPrincipalSource() {
    return new SecurityContextMcpPrincipalSource();
  }
}
