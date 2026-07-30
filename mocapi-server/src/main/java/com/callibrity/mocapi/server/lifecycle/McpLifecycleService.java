/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles MCP lifecycle notifications. With the 2026-07-28 stateless model (ADR-0020) the only
 * lifecycle notification left is {@code notifications/cancelled}; mocapi acknowledges it without
 * cancelling in-flight work (partial cancellation stance, ADR-0022). Note the spec made {@code
 * requestId} optional in this revision, so no presence validation happens here.
 */
public class McpLifecycleService {

  private final Logger log = LoggerFactory.getLogger(McpLifecycleService.class);

  @JsonRpcMethod(McpMethods.NOTIFICATIONS_CANCELLED)
  public EmptyResult cancelled(@JsonRpcParams CancelledNotificationParams params) {
    log.info(
        "Received cancellation for request {}, ignoring",
        params == null ? null : params.requestId());
    return EmptyResult.INSTANCE;
  }
}
