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
package com.callibrity.mocapi.oauth2.metadata;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.oauth2.MocapiOAuth2Properties;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ScopesSupportedMetadataCustomizerTest {

  @Test
  void adds_each_configured_scope_to_builder() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            null, List.of(), List.of("mcp.read", "mcp.write"), null, null, null);
    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);

    new ScopesSupportedMetadataCustomizer(props).customize(builder);

    verify(builder).scope("mcp.read");
    verify(builder).scope("mcp.write");
  }

  @Test
  void contributes_nothing_when_scopes_empty() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(null, List.of(), List.of(), null, null, null);
    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);

    new ScopesSupportedMetadataCustomizer(props).customize(builder);

    verify(builder, never()).scope(anyString());
  }
}
