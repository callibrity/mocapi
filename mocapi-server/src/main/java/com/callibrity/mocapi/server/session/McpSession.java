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
package com.callibrity.mocapi.server.session;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;

/** Immutable record of the client data captured during the MCP initialize handshake. */
public record McpSession(
    String sessionId,
    String protocolVersion,
    ClientCapabilities capabilities,
    Implementation clientInfo,
    LoggingLevel logLevel) {

  public static final ScopedValue<McpSession> CURRENT = ScopedValue.newInstance();

  /** Creates a session with the default log level (LoggingLevel.WARNING). */
  public McpSession(
      String sessionId,
      String protocolVersion,
      ClientCapabilities capabilities,
      Implementation clientInfo) {
    this(sessionId, protocolVersion, capabilities, clientInfo, LoggingLevel.WARNING);
  }

  /** Returns a copy of this session with the given log level. */
  public McpSession withLogLevel(LoggingLevel logLevel) {
    return new McpSession(sessionId, protocolVersion, capabilities, clientInfo, logLevel);
  }

  /** Returns true if the client supports form-based elicitation. */
  public boolean supportsElicitationForm() {
    return capabilities != null && capabilities.elicitation() != null;
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
