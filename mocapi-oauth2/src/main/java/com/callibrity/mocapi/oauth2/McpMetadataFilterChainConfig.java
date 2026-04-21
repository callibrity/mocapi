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

import com.callibrity.mocapi.oauth2.metadata.McpMetadataCustomizer;
import com.callibrity.mocapi.oauth2.token.McpTokenStrategy;
import java.util.List;

/**
 * Static inputs to {@link McpFilterChains#createMcpMetadataFilterChain}: the token strategy mocapi
 * uses to satisfy Spring Security's {@code oauth2ResourceServer} DSL (the metadata endpoint is
 * {@code permitAll} by RFC 9728, but the DSL refuses to build without a bearer-token format
 * declared), plus the ordered list of metadata-document and filter-chain customizers.
 *
 * <p>Mocapi's baseline document content (resource identifier, authorization servers, scopes
 * supported, resource name, documentation URIs) is contributed via five individual {@link
 * McpMetadataCustomizer} beans registered by {@code MocapiOAuth2AutoConfiguration} — not packed
 * into this config. User-supplied {@code McpMetadataCustomizer} beans join the same ordered stream;
 * override any baseline facet by registering a later-{@code @Order} customizer that overwrites the
 * field.
 *
 * @param tokenStrategy the bearer-token validation mode; required — used only to satisfy the DSL,
 *     does not enforce auth on the metadata path
 * @param metadataCustomizers user-supplied customizers that modify the metadata JSON document
 * @param chainCustomizers user-supplied customizers that tweak the security chain itself (CORS,
 *     headers, rate limiting — NOT auth policy; that is frozen at {@code permitAll} by RFC 9728)
 */
public record McpMetadataFilterChainConfig(
    McpTokenStrategy tokenStrategy,
    List<McpMetadataCustomizer> metadataCustomizers,
    List<McpMetadataFilterChainCustomizer> chainCustomizers) {}
