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
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration-fallback CSP/sandbox defaults for MCP Apps UI resources (ADR-0039). A
 * {@code @McpAppResource} whose {@code csp}/{@code sandbox} domain list resolves empty falls back
 * to the matching entry here, applied per-list rather than per-annotation — see {@link
 * AppsUiDefaults} for why.
 *
 * @param csp default CSP domain lists, applied per-list when a {@code @Csp} list is empty
 * @param sandbox default sandbox tokens, applied when {@code @McpAppResource#sandbox()} is empty
 */
@ConfigurationProperties(prefix = "mocapi.apps")
public record MocapiAppsProperties(CspDefaults csp, List<String> sandbox) {

  public MocapiAppsProperties {
    csp = csp == null ? new CspDefaults(null, null, null, null) : csp;
    sandbox = sandbox == null ? List.of() : List.copyOf(sandbox);
  }

  /**
   * @param connect default {@code connect} CSP domains
   * @param resource default {@code resource} CSP domains
   * @param frame default {@code frame} CSP domains
   * @param baseUri default {@code baseUri} CSP domains
   */
  public record CspDefaults(
      List<String> connect, List<String> resource, List<String> frame, List<String> baseUri) {

    public CspDefaults {
      connect = connect == null ? List.of() : List.copyOf(connect);
      resource = resource == null ? List.of() : List.copyOf(resource);
      frame = frame == null ? List.of() : List.copyOf(frame);
      baseUri = baseUri == null ? List.of() : List.copyOf(baseUri);
    }
  }

  AppsUiDefaults toAppsUiDefaults() {
    return new AppsUiDefaults(csp.connect(), csp.resource(), csp.frame(), csp.baseUri(), sandbox);
  }
}
