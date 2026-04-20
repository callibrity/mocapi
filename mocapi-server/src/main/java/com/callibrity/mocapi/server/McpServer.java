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

import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;

public interface McpServer {

  /**
   * The MCP protocol version this server build implements. Individual sessions may negotiate an
   * older version at initialize time; this is what the server advertises as its native version in
   * responses and protocol-version headers.
   */
  String PROTOCOL_VERSION = "2025-11-25";

  McpContextResult createContext(String sessionId, String protocolVersion);

  void handleCall(McpContext context, JsonRpcCall call, McpTransport transport);

  void handleNotification(McpContext context, JsonRpcNotification notification);

  void handleResponse(McpContext context, JsonRpcResponse response);

  void terminate(McpContext context);
}
