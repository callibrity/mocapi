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
package com.callibrity.mocapi.server.observability;

import com.callibrity.mocapi.server.session.McpSession;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;

/**
 * Sets SLF4J MDC correlation keys for the duration of an MCP handler invocation and removes exactly
 * the keys it added on {@link #close()}, leaving any pre-existing MDC entries untouched.
 */
public final class McpMdcScope implements AutoCloseable {

  private final List<String> addedKeys;

  private McpMdcScope(List<String> addedKeys) {
    this.addedKeys = addedKeys;
  }

  /**
   * Pushes correlation keys for a handler invocation and returns an {@link AutoCloseable} that
   * removes them on close. The current session id (when {@link McpSession#CURRENT} is bound) is
   * also pushed as {@link McpMdcKeys#SESSION}. Any {@code null} value is skipped.
   */
  public static McpMdcScope push(String handlerKind, String handlerName, String requestId) {
    List<String> added = new ArrayList<>(4);
    if (McpSession.CURRENT.isBound()) {
      String sessionId = McpSession.CURRENT.get().sessionId();
      putIfAbsent(McpMdcKeys.SESSION, sessionId, added);
    }
    putIfAbsent(McpMdcKeys.REQUEST, requestId, added);
    putIfAbsent(McpMdcKeys.HANDLER_KIND, handlerKind, added);
    putIfAbsent(McpMdcKeys.HANDLER_NAME, handlerName, added);
    return new McpMdcScope(added);
  }

  private static void putIfAbsent(String key, String value, List<String> added) {
    if (value == null) {
      return;
    }
    // Don't clobber a pre-existing MDC entry we didn't set — leave it (and don't track it for
    // removal) so upstream filters keep their state.
    if (MDC.get(key) != null) {
      return;
    }
    MDC.put(key, value);
    added.add(key);
  }

  @Override
  public void close() {
    for (String key : addedKeys) {
      MDC.remove(key);
    }
  }
}
