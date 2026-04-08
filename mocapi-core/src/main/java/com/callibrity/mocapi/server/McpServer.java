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

import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.tools.ToolsRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpServer {

  // ------------------------------ FIELDS ------------------------------

  public static final String PROTOCOL_VERSION = "2025-11-25";

  private final ServerInfo serverInfo;
  private final String instructions;
  private final Map<String, CapabilityDescriptor> serverCapabilities;

  // --------------------------- CONSTRUCTORS ---------------------------

  public McpServer(ToolsRegistry toolsRegistry, ServerInfo serverInfo, String instructions) {
    this.serverCapabilities = new HashMap<>();
    if (toolsRegistry != null) {
      serverCapabilities.put("tools", new ToolsCapabilityDescriptor(false));
    }
    this.serverInfo = serverInfo;
    this.instructions = instructions;
  }

  // -------------------------- OTHER METHODS --------------------------

  public void clientInitialized() {
    // Do nothing!
  }

  public InitializeResponse initialize(
      String protocolVersion, ClientCapabilities capabilities, ClientInfo clientInfo) {
    log.info(
        "Client {} initializing with protocol version {} and capabilities {}.",
        clientInfo,
        protocolVersion,
        capabilities);
    Map<String, CapabilityDescriptor> responseCapabilities = new HashMap<>(serverCapabilities);
    McpSession session = new McpSession(protocolVersion, capabilities, clientInfo);
    if (session.supportsElicitationForm()) {
      responseCapabilities.put("elicitation", new ElicitationCapabilityDescriptor());
    }
    return new InitializeResponse(PROTOCOL_VERSION, responseCapabilities, serverInfo, instructions);
  }

  /** Server-side elicitation capability descriptor. */
  public record ElicitationCapabilityDescriptor() implements CapabilityDescriptor {}

  /** Tools capability descriptor. */
  public record ToolsCapabilityDescriptor(boolean listChanged) implements CapabilityDescriptor {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record InitializeResponse(
      String protocolVersion,
      Map<String, CapabilityDescriptor> capabilities,
      ServerInfo serverInfo,
      String instructions) {}

  public PingResponse ping() {
    return new PingResponse();
  }

  public record PingResponse() {}
}
