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
package com.callibrity.mocapi.session;

/** Immutable record of the client data captured during the MCP initialize handshake. */
public record McpSession(
    String protocolVersion, ClientCapabilities capabilities, ClientInfo clientInfo) {

  /** Returns true if the client supports form-based elicitation. */
  public boolean supportsElicitationForm() {
    if (capabilities == null || capabilities.elicitation() == null) {
      return false;
    }
    // Per spec, empty elicitation object defaults to form-only support.
    return capabilities.elicitation().form() != null
        || (capabilities.elicitation().form() == null && capabilities.elicitation().url() == null);
  }

  /** Returns true if the client supports URL-based elicitation. */
  public boolean supportsElicitationUrl() {
    return capabilities != null
        && capabilities.elicitation() != null
        && capabilities.elicitation().url() != null;
  }

  /** Returns true if the client supports sampling. */
  public boolean supportsSampling() {
    return capabilities != null && capabilities.sampling() != null;
  }

  /** Returns true if the client supports roots. */
  public boolean supportsRoots() {
    return capabilities != null && capabilities.roots() != null;
  }
}
