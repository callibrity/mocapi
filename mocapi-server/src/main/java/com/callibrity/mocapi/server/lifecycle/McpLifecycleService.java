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
package com.callibrity.mocapi.server.lifecycle;

import com.callibrity.mocapi.model.CancelledNotificationParams;
import com.callibrity.mocapi.model.EmptyResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles MCP lifecycle notifications: {@code notifications/initialized}, {@code
 * notifications/cancelled}, and {@code notifications/roots/list_changed}.
 */
@Slf4j
public class McpLifecycleService {

  private final McpSessionService sessionService;

  public McpLifecycleService(McpSessionService sessionService) {
    this.sessionService = sessionService;
  }

  @JsonRpcMethod(McpMethods.NOTIFICATIONS_INITIALIZED)
  public EmptyResult initialized(McpSession session) {
    sessionService.markInitialized(session.sessionId());
    return EmptyResult.INSTANCE;
  }

  @JsonRpcMethod(McpMethods.NOTIFICATIONS_CANCELLED)
  public EmptyResult cancelled(@JsonRpcParams CancelledNotificationParams params) {
    log.info(
        "Received cancellation for request {}, ignoring",
        params == null ? null : params.requestId());
    return EmptyResult.INSTANCE;
  }

  @JsonRpcMethod(McpMethods.NOTIFICATIONS_ROOTS_LIST_CHANGED)
  public EmptyResult rootsListChanged() {
    log.debug("Received roots/list_changed notification, ignoring");
    return EmptyResult.INSTANCE;
  }
}
