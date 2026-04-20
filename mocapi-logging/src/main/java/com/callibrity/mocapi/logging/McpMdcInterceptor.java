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
package com.callibrity.mocapi.logging;

import static com.callibrity.mocapi.logging.McpMdcKeys.HANDLER_KIND;
import static com.callibrity.mocapi.logging.McpMdcKeys.HANDLER_NAME;
import static com.callibrity.mocapi.logging.McpMdcKeys.SESSION;

import com.callibrity.mocapi.server.session.McpSession;
import java.util.ArrayList;
import java.util.List;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import org.slf4j.MDC;

/**
 * Stamps SLF4J MDC attributes for the duration of every MCP handler invocation so every log line
 * emitted during the call — including lines from user handler code — carries correlation context
 * automatically.
 *
 * <p>Keys set: {@code mcp.handler.kind}, {@code mcp.handler.name}, and (when a session is bound to
 * the current scope) {@code mcp.session}. The interceptor removes exactly the keys it added on the
 * way out; pre-existing MDC state from upstream filters is preserved.
 *
 * <p>The handler kind and name are closed over at construction — one instance per handler, wired in
 * by the per-handler customizers in {@link MocapiLoggingAutoConfiguration}. The hot path does no
 * reflection.
 */
public final class McpMdcInterceptor implements MethodInterceptor<Object> {

  private final String handlerKind;
  private final String handlerName;

  public McpMdcInterceptor(String handlerKind, String handlerName) {
    this.handlerKind = handlerKind;
    this.handlerName = handlerName;
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    var sessionId = McpSession.CURRENT.isBound() ? McpSession.CURRENT.get().sessionId() : null;

    var added = new ArrayList<String>(3);
    putIfPresent(added, HANDLER_KIND, handlerKind);
    putIfPresent(added, HANDLER_NAME, blankToNull(handlerName));
    putIfPresent(added, SESSION, sessionId);

    try {
      return invocation.proceed();
    } finally {
      for (String key : added) {
        MDC.remove(key);
      }
    }
  }

  private static void putIfPresent(List<String> added, String key, String value) {
    if (value != null) {
      MDC.put(key, value);
      added.add(key);
    }
  }

  private static String blankToNull(String value) {
    return (value == null || value.isEmpty()) ? null : value;
  }
}
