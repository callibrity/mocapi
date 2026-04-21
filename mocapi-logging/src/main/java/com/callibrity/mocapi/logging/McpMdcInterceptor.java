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

import static com.callibrity.mocapi.logging.McpMdcKeys.HANDLER_CLASS;
import static com.callibrity.mocapi.logging.McpMdcKeys.HANDLER_KIND;
import static com.callibrity.mocapi.logging.McpMdcKeys.HANDLER_NAME;
import static com.callibrity.mocapi.logging.McpMdcKeys.PROTOCOL_VERSION;
import static com.callibrity.mocapi.logging.McpMdcKeys.REQUEST_ID;
import static com.callibrity.mocapi.logging.McpMdcKeys.SESSION;

import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcRequest;
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
 * <p>Keys set (when their source value is available):
 *
 * <ul>
 *   <li>{@link McpMdcKeys#HANDLER_KIND} — tool / prompt / resource / resource_template
 *   <li>{@link McpMdcKeys#HANDLER_NAME} — tool name, prompt name, resource URI, or URI template
 *   <li>{@link McpMdcKeys#HANDLER_CLASS} — simple name of the (unwrapped) bean class
 *   <li>{@link McpMdcKeys#SESSION} — current MCP session id (from {@link McpSession#CURRENT})
 *   <li>{@link McpMdcKeys#PROTOCOL_VERSION} — negotiated protocol version (from the session)
 *   <li>{@link McpMdcKeys#REQUEST_ID} — JSON-RPC request id for the current call (from {@link
 *       JsonRpcDispatcher#CURRENT_REQUEST}; absent for notifications)
 * </ul>
 *
 * <p>The interceptor removes exactly the keys it added on the way out — pre-existing MDC state from
 * upstream filters is preserved.
 *
 * <p>Handler kind, name, and class are closed over at construction (one instance per handler, wired
 * by {@code MocapiLoggingAutoConfiguration}'s per-kind customizer beans) so the hot path does no
 * reflection. Session, protocol version, and request id are read per-call from the bound {@link
 * ScopedValue}s.
 */
public final class McpMdcInterceptor implements MethodInterceptor<Object> {

  private final HandlerKind handlerKind;
  private final String handlerName;
  private final String handlerClass;

  public McpMdcInterceptor(HandlerKind handlerKind, String handlerName, String handlerClass) {
    this.handlerKind = handlerKind;
    this.handlerName = handlerName;
    this.handlerClass = handlerClass;
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    String sessionId = null;
    String protocolVersion = null;
    if (McpSession.CURRENT.isBound()) {
      McpSession session = McpSession.CURRENT.get();
      sessionId = session.sessionId();
      protocolVersion = session.protocolVersion();
    }

    String requestId = null;
    if (JsonRpcDispatcher.CURRENT_REQUEST.isBound()) {
      JsonRpcRequest request = JsonRpcDispatcher.CURRENT_REQUEST.get();
      if (request instanceof JsonRpcCall call) {
        requestId = call.id().asString();
      }
    }

    var added = new ArrayList<String>(6);
    putIfPresent(added, HANDLER_KIND, handlerKind == null ? null : handlerKind.tag());
    putIfPresent(added, HANDLER_NAME, blankToNull(handlerName));
    putIfPresent(added, HANDLER_CLASS, blankToNull(handlerClass));
    putIfPresent(added, SESSION, sessionId);
    putIfPresent(added, PROTOCOL_VERSION, protocolVersion);
    putIfPresent(added, REQUEST_ID, requestId);

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

  @Override
  public String toString() {
    return "Stamps SLF4J MDC correlation keys for "
        + (handlerKind == null ? "null" : handlerKind.tag())
        + " '"
        + handlerName
        + "'";
  }
}
