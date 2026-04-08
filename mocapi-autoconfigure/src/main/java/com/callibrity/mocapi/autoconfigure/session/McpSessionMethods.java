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
package com.callibrity.mocapi.autoconfigure.session;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import lombok.RequiredArgsConstructor;

@JsonRpcService
@RequiredArgsConstructor
public class McpSessionMethods {

  private final McpServer mcpServer;

  @JsonRpc("initialize")
  public McpServer.InitializeResponse initialize(
      String protocolVersion, ClientCapabilities capabilities, ClientInfo clientInfo) {
    return mcpServer.initialize(protocolVersion, capabilities, clientInfo);
  }

  @JsonRpc("ping")
  public McpServer.PingResponse ping() {
    return mcpServer.ping();
  }

  @JsonRpc("notifications/initialized")
  public void initialized() {
    mcpServer.clientInitialized();
  }
}
