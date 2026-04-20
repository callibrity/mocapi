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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.intercept.MethodInvocation;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditLoggingInterceptorTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private final Logger logger = (Logger) LoggerFactory.getLogger("mocapi.audit");
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void emits_success_event_with_base_fields() {
    var interceptor = newInterceptor("tool", "weather", false);
    interceptor.intercept(invocation(Map.of("city", "Cincinnati"), () -> "ok"));

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getMessage()).isEqualTo("mcp.audit");
    Map<String, Object> kv = keyValues(event);
    assertThat(kv)
        .containsEntry(AuditFieldKeys.CALLER, "alice")
        .containsEntry(AuditFieldKeys.HANDLER_KIND, "tool")
        .containsEntry(AuditFieldKeys.HANDLER_NAME, "weather")
        .containsEntry(AuditFieldKeys.OUTCOME, "success")
        .containsKey(AuditFieldKeys.DURATION_MS)
        .doesNotContainKey(AuditFieldKeys.ARGUMENTS_HASH)
        .doesNotContainKey(AuditFieldKeys.ERROR_CLASS)
        .containsKey(AuditFieldKeys.SESSION_ID);
    assertThat((Long) kv.get(AuditFieldKeys.DURATION_MS)).isGreaterThanOrEqualTo(0L);
    assertThat(kv.get(AuditFieldKeys.SESSION_ID)).isNull();
  }

  @Test
  void emits_session_id_when_session_is_bound() {
    var interceptor = newInterceptor("tool", "weather", false);
    var session = new McpSession("session-42", "2025-11-25", null, null);
    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> interceptor.intercept(invocation(Map.of(), () -> "ok")));

    Map<String, Object> kv = keyValues(appender.list.getFirst());
    assertThat(kv).containsEntry(AuditFieldKeys.SESSION_ID, "session-42");
  }

  @Test
  void classifies_forbidden() {
    var interceptor = newInterceptor("tool", "wipe", false);
    var invocation =
        invocation(
            Map.of(),
            () -> {
              throw new JsonRpcException(JsonRpcErrorCodes.FORBIDDEN, "Forbidden: nope");
            });
    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(JsonRpcException.class);

    Map<String, Object> kv = keyValues(appender.list.getFirst());
    assertThat(kv)
        .containsEntry(AuditFieldKeys.OUTCOME, "forbidden")
        .containsEntry(AuditFieldKeys.ERROR_CLASS, "JsonRpcException");
  }

  @Test
  void classifies_invalid_params() {
    var interceptor = newInterceptor("tool", "weather", false);
    var invocation =
        invocation(
            Map.of(),
            () -> {
              throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "missing city");
            });
    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(JsonRpcException.class);

    Map<String, Object> kv = keyValues(appender.list.getFirst());
    assertThat(kv).containsEntry(AuditFieldKeys.OUTCOME, "invalid_params");
  }

  @Test
  void classifies_error_for_arbitrary_runtime_exception() {
    var interceptor = newInterceptor("tool", "weather", false);
    var invocation =
        invocation(
            Map.of(),
            () -> {
              throw new IllegalStateException("boom");
            });
    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(IllegalStateException.class);

    Map<String, Object> kv = keyValues(appender.list.getFirst());
    assertThat(kv)
        .containsEntry(AuditFieldKeys.OUTCOME, "error")
        .containsEntry(AuditFieldKeys.ERROR_CLASS, "IllegalStateException");
  }

  @Test
  void emits_arguments_hash_only_when_opted_in() {
    var interceptor = newInterceptor("tool", "weather", true);
    interceptor.intercept(invocation(Map.of("city", "Cincinnati"), () -> "ok"));

    Map<String, Object> kv = keyValues(appender.list.getFirst());
    String hash = (String) kv.get(AuditFieldKeys.ARGUMENTS_HASH);
    assertThat(hash).isNotNull().startsWith("sha256:").hasSize("sha256:".length() + 64);
  }

  @Test
  void arguments_hash_is_stable_across_key_order() {
    var interceptor = newInterceptor("tool", "weather", true);
    interceptor.intercept(
        invocation(MAPPER.createObjectNode().put("a", 1).put("b", 2).put("c", 3), () -> "ok"));
    interceptor.intercept(
        invocation(MAPPER.createObjectNode().put("c", 3).put("b", 2).put("a", 1), () -> "ok"));

    String first = (String) keyValues(appender.list.get(0)).get(AuditFieldKeys.ARGUMENTS_HASH);
    String second = (String) keyValues(appender.list.get(1)).get(AuditFieldKeys.ARGUMENTS_HASH);
    assertThat(first).isEqualTo(second);
  }

  @Test
  void arguments_hash_differs_for_different_payloads() {
    var interceptor = newInterceptor("tool", "weather", true);
    interceptor.intercept(invocation(Map.of("city", "Cincinnati"), () -> "ok"));
    interceptor.intercept(invocation(Map.of("city", "Columbus"), () -> "ok"));

    String first = (String) keyValues(appender.list.get(0)).get(AuditFieldKeys.ARGUMENTS_HASH);
    String second = (String) keyValues(appender.list.get(1)).get(AuditFieldKeys.ARGUMENTS_HASH);
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void falls_back_to_anonymous_when_caller_provider_throws() {
    AuditCallerIdentityProvider throwing =
        () -> {
          throw new RuntimeException("provider exploded");
        };
    var interceptor = new AuditLoggingInterceptor("tool", "weather", throwing, false, MAPPER);
    interceptor.intercept(invocation(Map.of(), () -> "ok"));

    Map<String, Object> kv = keyValues(appender.list.getFirst());
    assertThat(kv).containsEntry(AuditFieldKeys.CALLER, AuditCallerIdentityProvider.ANONYMOUS);
  }

  private static AuditLoggingInterceptor newInterceptor(
      String kind, String name, boolean hashArguments) {
    return new AuditLoggingInterceptor(kind, name, () -> "alice", hashArguments, MAPPER);
  }

  private static <A> MethodInvocation<A> invocation(A argument, Supplier<Object> body) {
    return MethodInvocation.of(dummyMethod(), new Object(), argument, new Object[0], body);
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  private static Map<String, Object> keyValues(ILoggingEvent event) {
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    Map<String, Object> out = new LinkedHashMap<>();
    if (pairs != null) {
      for (KeyValuePair pair : pairs) {
        out.put(pair.key, pair.value);
      }
    }
    return out;
  }
}
