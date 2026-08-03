/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.apps;

import java.util.List;

/**
 * Config-sourced fallbacks for {@code @McpAppResource}'s {@code csp}/{@code sandbox} attributes
 * (mocapi-autoconfigure builds this from {@code mocapi.apps.*} properties). Kept as a plain record
 * here — rather than a {@code @ConfigurationProperties} type — so this code-only module stays free
 * of a Spring Boot dependency; the autoconfigure module owns the property binding and passes the
 * resolved values in.
 *
 * <p>Each CSP list falls back independently: a {@code @Csp} domain list that resolves empty uses
 * the matching config default instead. {@code @Csp} being its own nested annotation with an
 * all-default instance is structurally indistinguishable from "not specified" once elements are
 * empty — the same "explicitly empty can't be expressed" edge case the {@code sandbox} attribute
 * already has, so both are handled identically (per-list, not per-annotation).
 */
public record AppsUiDefaults(
    List<String> cspConnect,
    List<String> cspResource,
    List<String> cspFrame,
    List<String> cspBaseUri,
    List<String> sandbox) {

  public AppsUiDefaults {
    cspConnect = cspConnect == null ? List.of() : List.copyOf(cspConnect);
    cspResource = cspResource == null ? List.of() : List.copyOf(cspResource);
    cspFrame = cspFrame == null ? List.of() : List.copyOf(cspFrame);
    cspBaseUri = cspBaseUri == null ? List.of() : List.copyOf(cspBaseUri);
    sandbox = sandbox == null ? List.of() : List.copyOf(sandbox);
  }

  /** No configured fallbacks — every list empty. */
  public static AppsUiDefaults none() {
    return new AppsUiDefaults(List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
