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
package com.callibrity.mocapi.transport.http;

import java.net.URI;
import java.util.List;

/**
 * Validates MCP transport-level headers. Protocol validation (session, protocol version) is handled
 * by {@link com.callibrity.mocapi.server.McpServer#validate}.
 */
public class McpRequestValidator {

  private final List<String> allowedOrigins;

  public McpRequestValidator(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
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
