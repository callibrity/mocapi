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

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the mocapi OAuth2 resource-server module. The module's auto-configuration fires
 * whenever it is on the classpath; apps that don't want OAuth2 should not depend on {@code
 * mocapi-oauth2} / {@code mocapi-oauth2-spring-boot-starter}.
 *
 * <p>Token validation ({@code JwtDecoder}, issuer, audience) is configured through the standard
 * Spring Boot properties {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} and {@code
 * spring.security.oauth2.resourceserver.jwt.audiences}. Those are already auto-wired by Spring
 * Boot's resource-server auto-configuration and this module does not duplicate them.
 *
 * <p>What this module adds: the MCP-specific pieces Spring does not fill in — the RFC 9728
 * protected-resource metadata document content, and scoping a dedicated Spring Security filter
 * chain to the MCP endpoint path.
 *
 * @param resource the absolute URL of this MCP server — the {@code resource} claim in the
 *     protected-resource metadata document. When omitted, defaults to {@code
 *     spring.security.oauth2.resourceserver.jwt.audiences[0]} if exactly one audience is
 *     configured; otherwise the module fails at startup asking for an explicit value. The resolved
 *     resource must be a member of the configured audiences set — otherwise clients would follow
 *     the metadata, obtain tokens with a matching {@code aud} claim, and the server would reject
 *     them during validation. Mocapi enforces this invariant at startup.
 * @param authorizationServers OAuth2 authorization servers trusted to issue tokens for this
 *     resource. When empty (the default), the module falls back to the issuer configured via {@code
 *     spring.security.oauth2.resourceserver.jwt.issuer-uri} so single-IdP setups don't have to
 *     restate the value. Set explicitly to federate across multiple authorization servers.
 * @param scopes scopes the resource server advertises as supported. Informational; enforcement is
 *     the caller's responsibility via {@link MocapiOAuth2SecurityFilterChainCustomizer}.
 * @param resourceDocumentation URL of human-readable developer documentation for the resource (RFC
 *     9728 §2 {@code resource_documentation}). Optional.
 * @param resourcePolicyUri URL of a policy document describing how access tokens and data are
 *     handled by this resource — privacy, retention, rate limiting, etc. (RFC 9728 {@code
 *     resource_policy_uri}). Optional.
 * @param resourceTosUri URL of the terms-of-service document clients must accept to use this
 *     resource (RFC 9728 {@code resource_tos_uri}). Optional.
 *     <p>The metadata document's {@code resource_name} field is automatically populated from the
 *     MCP {@link com.callibrity.mocapi.model.Implementation Implementation} server-info bean
 *     ({@code mocapi.server-title}, falling back to {@code mocapi.server-name}), so there is no
 *     separate property for it — mocapi avoids maintaining two sources of truth for the same
 *     human-readable server label.
 */
@ConfigurationProperties("mocapi.oauth2")
@Validated
public record MocapiOAuth2Properties(
    String resource,
    @DefaultValue List<String> authorizationServers,
    @DefaultValue List<String> scopes,
    String resourceDocumentation,
    String resourcePolicyUri,
    String resourceTosUri) {}
