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

import com.callibrity.mocapi.model.Implementation;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Pure-logic helpers that derive values mocapi publishes in its RFC 9728 protected-resource
 * metadata document: the {@code resource} identifier, the {@code authorization_servers} list, and
 * the human-readable {@code resource_name}.
 *
 * <p>Takes already-resolved concrete values (not {@code ObjectProvider}s) so each helper can be
 * exercised directly from unit tests without a Spring context.
 */
public final class MocapiOAuth2ResourceResolver {

  private MocapiOAuth2ResourceResolver() {}

  /**
   * Resolves the {@code resource} identifier mocapi advertises in its RFC 9728 protected-resource
   * metadata document. Three paths:
   *
   * <ul>
   *   <li>Explicitly set via {@code mocapi.oauth2.resource} — used as-is, but asserted to be a
   *       member of {@code audiences} so clients that follow the metadata and request tokens for
   *       that resource will produce an {@code aud} claim the server actually accepts. A mismatch
   *       here would silently break every client.
   *   <li>Unset, audiences has exactly one element — mocapi defaults the resource to that single
   *       audience. Common case for simple setups (one Auth0 API, one Okta authorization server,
   *       one Keycloak client) where duplicating the same string in two properties is pure
   *       ceremony.
   *   <li>Unset, audiences has zero or multiple elements — ambiguous. The zero case is already
   *       rejected by {@link MocapiOAuth2Compliance#validate} with a clearer message; the
   *       multi-audience case fails here asking for an explicit resource value.
   * </ul>
   */
  public static String resolve(MocapiOAuth2Properties properties, List<String> audiences) {
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
      return explicit;
    }
    if (audiences.size() == 1) {
      return audiences.get(0);
    }
    throw new IllegalStateException(
        String.format(
            "mocapi.oauth2.resource is not set and cannot be derived: "
                + "spring.security.oauth2.resourceserver.jwt.audiences has %d entries (%s). "
                + "Mocapi can default resource to the single audience when there is exactly one; "
                + "otherwise set mocapi.oauth2.resource explicitly to pick which audience value "
                + "should be published in the protected-resource metadata document.",
            audiences.size(), audiences));
  }

  /**
   * Returns the authorization-servers list mocapi advertises in the metadata document. Uses the
   * explicit {@code mocapi.oauth2.authorization-servers} property when set; otherwise falls back to
   * the standard Spring Boot resource-server {@code issuer-uri}, so single-IdP setups don't have to
   * restate the value. Returns an empty list when neither source is available.
   *
   * @param properties the mocapi-oauth2 properties bean
   * @param springIssuerUri the value of {@code
   *     spring.security.oauth2.resourceserver.jwt.issuer-uri} — {@code null} or blank when unset
   */
  public static List<String> authorizationServers(
      MocapiOAuth2Properties properties, String springIssuerUri) {
    if (!properties.authorizationServers().isEmpty()) {
      return properties.authorizationServers();
    }
    return StringUtils.isBlank(springIssuerUri) ? List.of() : List.of(springIssuerUri);
  }

  /**
   * Picks the human-readable resource name from the MCP {@link Implementation} server-info bean —
   * {@code title} when set, otherwise {@code name}. Returns empty when no server-info bean is
   * available (e.g., in a tightly-scoped test context that doesn't autowire the streamable-http
   * auto-configuration), or when both {@code title} and {@code name} are blank.
   */
  public static Optional<String> resourceName(Implementation impl) {
    if (impl == null) {
      return Optional.empty();
    }
    if (StringUtils.isNotBlank(impl.title())) {
      return Optional.of(impl.title());
    }
    if (StringUtils.isNotBlank(impl.name())) {
      return Optional.of(impl.name());
    }
    return Optional.empty();
  }
}
