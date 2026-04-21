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

import com.callibrity.mocapi.oauth2.MocapiOAuth2Properties;
import java.util.List;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Mocapi baseline {@link McpMetadataCustomizer} that populates {@code scopes_supported} from {@code
 * mocapi.oauth2.scopes}. Contributes nothing when no scopes are configured.
 */
public final class ScopesSupportedMetadataCustomizer implements McpMetadataCustomizer {

  private final List<String> scopes;

  public ScopesSupportedMetadataCustomizer(MocapiOAuth2Properties properties) {
    this.scopes = List.copyOf(properties.scopes());
  }

  @Override
  public void customize(OAuth2ProtectedResourceMetadata.Builder builder) {
    scopes.forEach(builder::scope);
  }
}
