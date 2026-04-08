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
package com.callibrity.mocapi.server;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Validates MCP protocol headers. Transport-agnostic — returns validation results rather than HTTP
 * responses.
 */
public class McpRequestValidator {

  private static final Set<String> KNOWN_PROTOCOL_VERSIONS =
      Set.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

  private final List<String> allowedOrigins;

  public McpRequestValidator(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  /** Validates the MCP-Protocol-Version header. Defaults to the current version if null. */
  public boolean isValidProtocolVersion(String protocolVersion) {
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    return KNOWN_PROTOCOL_VERSIONS.contains(version);
  }

  /** Validates the Origin header against the allowed origins list. Null origin is always valid. */
  public boolean isValidOrigin(String origin) {
    if (origin == null) {
      return true;
    }
    try {
      String host = URI.create(origin).getHost();
      return host != null && allowedOrigins.contains(host);
    } catch (IllegalArgumentException _) {
      return false;
    }
  }
}
