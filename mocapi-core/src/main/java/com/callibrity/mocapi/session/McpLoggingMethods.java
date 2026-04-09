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

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import lombok.RequiredArgsConstructor;

@JsonRpcService
@RequiredArgsConstructor
public class McpLoggingMethods {

  private final McpSessionService sessionService;

  @JsonRpcMethod("logging/setLevel")
  public Object setLevel(String level) {
    LogLevel logLevel;
    try {
      logLevel = LogLevel.fromString(level);
    } catch (IllegalArgumentException e) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid log level: " + level);
    }
    String sessionId = McpSession.CURRENT.get().sessionId();
    try {
      sessionService.setLogLevel(sessionId, logLevel);
    } catch (IllegalArgumentException e) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, e.getMessage());
    }
    return new Object() {};
  }
}
