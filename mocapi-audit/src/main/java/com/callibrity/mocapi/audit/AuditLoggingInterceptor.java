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
package com.callibrity.mocapi.audit;

import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.util.Hashes;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Emits one structured audit event per MCP handler invocation on the {@code mocapi.audit} SLF4J
 * logger, with caller / session / handler / outcome / duration fields and (opt-in) a SHA-256 hash
 * of the arguments. One instance per handler — {@code handlerKind} and {@code handlerName} are
 * closed over at construction so the hot path does no reflection.
 *
 * <p>Outcome classification maps {@link JsonRpcException}s carrying {@link
 * JsonRpcErrorCodes#FORBIDDEN} to {@code forbidden}, those carrying {@link
 * JsonRpcProtocol#INVALID_PARAMS} to {@code invalid_params}, everything else to {@code error}. A
 * protocol-level success (including {@code CallToolResult.isError=true}) stays {@code success};
 * audit measures infrastructure-level outcome, not tool semantics.
 */
public final class AuditLoggingInterceptor implements MethodInterceptor<Object> {

  private static final Logger log = LoggerFactory.getLogger("mocapi.audit");
  private static final String EVENT_MESSAGE = "mcp.audit";

  private final String handlerKind;
  private final String handlerName;
  private final AuditCallerIdentityProvider callerProvider;
  private final boolean hashArguments;
  private final ObjectMapper canonicalMapper;

  public AuditLoggingInterceptor(
      String handlerKind,
      String handlerName,
      AuditCallerIdentityProvider callerProvider,
      boolean hashArguments,
      ObjectMapper objectMapper) {
    this.handlerKind = handlerKind;
    this.handlerName = handlerName;
    this.callerProvider = callerProvider;
    this.hashArguments = hashArguments;
    this.canonicalMapper = objectMapper;
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    long startNanos = System.nanoTime();
    String caller = safeCaller();
    String sessionId = safeSessionId();
    String argsHash = hashArguments ? computeArgsHash(invocation.argument()) : null;

    String outcome = "success";
    Throwable failure = null;
    try {
      return invocation.proceed();
    } catch (RuntimeException | Error t) {
      outcome = classifyOutcome(t);
      failure = t;
      throw t;
    } finally {
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
      LoggingEventBuilder event =
          log.atInfo()
              .addKeyValue(AuditFieldKeys.CALLER, caller)
              .addKeyValue(AuditFieldKeys.SESSION_ID, sessionId)
              .addKeyValue(AuditFieldKeys.HANDLER_KIND, handlerKind)
              .addKeyValue(AuditFieldKeys.HANDLER_NAME, handlerName)
              .addKeyValue(AuditFieldKeys.OUTCOME, outcome)
              .addKeyValue(AuditFieldKeys.DURATION_MS, durationMs);
      if (argsHash != null) {
        event = event.addKeyValue(AuditFieldKeys.ARGUMENTS_HASH, argsHash);
      }
      if (failure != null) {
        event = event.addKeyValue(AuditFieldKeys.ERROR_CLASS, failure.getClass().getSimpleName());
      }
      event.log(EVENT_MESSAGE);
    }
  }

  private String safeCaller() {
    try {
      String caller = callerProvider.currentCaller();
      return (caller == null || caller.isEmpty()) ? AuditCallerIdentityProvider.ANONYMOUS : caller;
    } catch (RuntimeException _) {
      return AuditCallerIdentityProvider.ANONYMOUS;
    }
  }

  private static String safeSessionId() {
    return McpSession.CURRENT.isBound() ? McpSession.CURRENT.get().sessionId() : null;
  }

  private static String classifyOutcome(Throwable t) {
    if (t instanceof JsonRpcException jre) {
      int code = jre.getCode();
      if (code == JsonRpcErrorCodes.FORBIDDEN) {
        return "forbidden";
      }
      if (code == JsonRpcProtocol.INVALID_PARAMS) {
        return "invalid_params";
      }
    }
    return "error";
  }

  private String computeArgsHash(Object argument) {
    try {
      JsonNode canonical = sortKeys(canonicalMapper.valueToTree(argument));
      byte[] bytes =
          canonicalMapper
              .writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
              .writeValueAsBytes(canonical);
      return Hashes.sha256Of(bytes);
    } catch (RuntimeException _) {
      return null;
    }
  }

  private static JsonNode sortKeys(JsonNode node) {
    if (node == null || node.isNull()) {
      return JsonNodeFactory.instance.nullNode();
    }
    if (node instanceof ObjectNode on) {
      List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
      on.propertyStream().forEach(entries::add);
      entries.sort(Comparator.comparing(Map.Entry::getKey));
      ObjectNode sorted = JsonNodeFactory.instance.objectNode();
      for (Map.Entry<String, JsonNode> entry : entries) {
        sorted.set(entry.getKey(), sortKeys(entry.getValue()));
      }
      return sorted;
    }
    if (node instanceof ArrayNode an) {
      ArrayNode sortedArray = JsonNodeFactory.instance.arrayNode();
      for (JsonNode child : an) {
        sortedArray.add(sortKeys(child));
      }
      return sortedArray;
    }
    return node;
  }
}
