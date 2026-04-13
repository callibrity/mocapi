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
package com.callibrity.mocapi.server.logging;

import com.callibrity.mocapi.model.EmptyResult;
import com.callibrity.mocapi.model.LoggingCapability;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.SetLevelRequestParams;
import com.callibrity.mocapi.server.ServerCapabilitiesBuilder;
import com.callibrity.mocapi.server.ServerCapabilitiesContributor;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;

/** Handles logging/setLevel by updating the session's log level via McpSessionService. */
@JsonRpcService
public class McpLoggingService implements ServerCapabilitiesContributor {

  private final McpSessionService sessionService;

  public McpLoggingService(McpSessionService sessionService) {
    this.sessionService = sessionService;
  }

  @JsonRpcMethod(McpMethods.LOGGING_SET_LEVEL)
  public EmptyResult setLevel(McpSession session, @JsonRpcParams SetLevelRequestParams params) {
    sessionService.setLogLevel(session.sessionId(), params.level());
    return EmptyResult.INSTANCE;
  }

  @Override
  public void contribute(ServerCapabilitiesBuilder builder) {
    builder.logging(new LoggingCapability());
  }
}
