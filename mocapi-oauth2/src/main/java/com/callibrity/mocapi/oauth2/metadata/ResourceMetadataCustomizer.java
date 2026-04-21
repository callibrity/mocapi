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
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Mocapi baseline {@link McpMetadataCustomizer} that sets the RFC 9728 {@code resource} identifier
 * on the protected-resource metadata document. Three resolution paths, checked in order:
 *
 * <ul>
 *   <li>Explicitly set via {@code mocapi.oauth2.resource} — used as-is, but asserted to be a member
 *       of the configured {@code audiences} so clients that follow the metadata and request tokens
 *       for that resource produce an {@code aud} claim the server will accept.
 *   <li>Unset with exactly one audience configured — defaults to that single audience. Common case
 *       for simple single-IdP setups where duplicating the same string in two properties is
 *       ceremony.
 *   <li>Unset with zero or multiple audiences — ambiguous; fails fast at construction time with a
 *       descriptive message telling the operator to set {@code mocapi.oauth2.resource} explicitly.
 * </ul>
 */
public final class ResourceMetadataCustomizer implements McpMetadataCustomizer {

  private final String resource;

  public ResourceMetadataCustomizer(
      MocapiOAuth2Properties properties, OAuth2ResourceServerProperties resourceServerProperties) {
    List<String> audiences = resourceServerProperties.getJwt().getAudiences();
    String explicit = properties.resource();
    if (StringUtils.isNotBlank(explicit)) {
      if (!audiences.contains(explicit)) {
        throw new IllegalStateException(
            String.format(
                "mocapi.oauth2.resource (%s) is not a member of "
                    + "spring.security.oauth2.resourceserver.jwt.audiences (%s). Clients that "
                    + "follow the protected-resource metadata document would request tokens with "
                    + "aud=%s, and this server would then reject those tokens during audience "
                    + "validation. Set mocapi.oauth2.resource to one of the configured audiences, "
                    + "or add the resource value to the audiences list.",
                explicit, audiences, explicit));
      }
      this.resource = explicit;
    } else if (audiences.size() == 1) {
      this.resource = audiences.getFirst();
    } else {
      throw new IllegalStateException(
          String.format(
              "mocapi.oauth2.resource is not set and cannot be derived: "
                  + "spring.security.oauth2.resourceserver.jwt.audiences has %d entries (%s). "
                  + "Mocapi can default resource to the single audience when there is exactly one; "
                  + "otherwise set mocapi.oauth2.resource explicitly to pick which audience value "
                  + "should be published in the protected-resource metadata document.",
              audiences.size(), audiences));
    }
  }

  @Override
  public void customize(OAuth2ProtectedResourceMetadata.Builder builder) {
    builder.resource(resource);
  }
}
