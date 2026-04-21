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
package com.callibrity.mocapi.oauth2.token;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OpaqueTokenMcpTokenStrategyTest {

  @Test
  void apply_registers_opaque_token_introspector_via_configurer() {
    OpaqueTokenIntrospector delegate = mock(OpaqueTokenIntrospector.class);
    OpaqueTokenMcpTokenStrategy strategy =
        new OpaqueTokenMcpTokenStrategy(delegate, List.of("mcp-test"));

    var oauth2 = mock(OAuth2ResourceServerConfigurer.class);
    strategy.apply(oauth2);

    verify(oauth2).opaqueToken(any(Customizer.class));
  }
}
