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
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Mocapi baseline {@link McpMetadataCustomizer} that populates the optional extension claims {@code
 * resource_documentation}, {@code resource_policy_uri}, and {@code resource_tos_uri} on the
 * protected-resource metadata document — whichever of the corresponding {@link
 * MocapiOAuth2Properties} fields are set.
 */
public final class ClaimsMetadataCustomizer implements McpMetadataCustomizer {

  private final String documentation;
  private final String policyUri;
  private final String tosUri;

  public ClaimsMetadataCustomizer(MocapiOAuth2Properties properties) {
    this.documentation = properties.resourceDocumentation();
    this.policyUri = properties.resourcePolicyUri();
    this.tosUri = properties.resourceTosUri();
  }

  @Override
  public void customize(OAuth2ProtectedResourceMetadata.Builder builder) {
    if (documentation != null) {
      builder.claim("resource_documentation", documentation);
    }
    if (policyUri != null) {
      builder.claim("resource_policy_uri", policyUri);
    }
    if (tosUri != null) {
      builder.claim("resource_tos_uri", tosUri);
    }
  }
}
