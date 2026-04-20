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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.session.McpSession;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import org.slf4j.MDC;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpMdcInterceptorTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void sets_configured_kind_and_name_for_the_duration_of_proceed() throws Exception {
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, "my-tool", "Fixtures");
    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get())
        .containsEntry(McpMdcKeys.HANDLER_KIND, "tool")
        .containsEntry(McpMdcKeys.HANDLER_NAME, "my-tool");
    assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isNull();
    assertThat(MDC.get(McpMdcKeys.HANDLER_NAME)).isNull();
  }

  @Test
  void sets_session_key_when_session_is_bound() {
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, "my-tool", "Fixtures");
    var session = new McpSession("session-42", "2025-11-25", null, null);
    AtomicReference<String> seen = new AtomicReference<>();

    ScopedValue.where(McpSession.CURRENT, session)
        .run(
            () -> {
              try {
                interceptor.intercept(
                    MethodInvocation.of(
                        dummyMethod(),
                        new Fixtures(),
                        null,
                        new Object[0],
                        () -> {
                          seen.set(MDC.get(McpMdcKeys.SESSION));
                          return null;
                        }));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(seen.get()).isEqualTo("session-42");
    assertThat(MDC.get(McpMdcKeys.SESSION)).isNull();
  }

  @Test
  void omits_session_key_when_session_is_not_bound() {
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, "my-tool", "Fixtures");
    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get().containsKey(McpMdcKeys.SESSION)).isFalse();
  }

  @Test
  void removes_keys_even_when_proceed_throws() throws Exception {
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, "my-tool", "Fixtures");
    var invocation =
        MethodInvocation.of(
            dummyMethod(),
            new Fixtures(),
            null,
            new Object[0],
            () -> {
              throw new IllegalStateException("boom");
            });

    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isNull();
    assertThat(MDC.get(McpMdcKeys.HANDLER_NAME)).isNull();
  }

  @Test
  void preserves_pre_existing_mdc_entries() {
    MDC.put("upstream.trace", "abc-123");
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, "my-tool", "Fixtures");

    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get()).containsEntry("upstream.trace", "abc-123");
    assertThat(MDC.get("upstream.trace")).isEqualTo("abc-123");
  }

  @Test
  void omits_name_key_when_handler_name_is_blank() {
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, "", "Fixtures");
    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get()).containsEntry(McpMdcKeys.HANDLER_KIND, "tool");
    assertThat(captured.get().containsKey(McpMdcKeys.HANDLER_NAME)).isFalse();
  }

  @Test
  void omits_name_key_when_handler_name_is_null() {
    var interceptor = new McpMdcInterceptor(HandlerKind.TOOL, null, "Fixtures");
    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get()).containsEntry(McpMdcKeys.HANDLER_KIND, "tool");
    assertThat(captured.get().containsKey(McpMdcKeys.HANDLER_NAME)).isFalse();
  }

  @Test
  void resource_kind_and_uri_render_as_handler_name() {
    var interceptor = new McpMdcInterceptor(HandlerKind.RESOURCE, "mem://hello", "Fixtures");
    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get())
        .containsEntry(McpMdcKeys.HANDLER_KIND, "resource")
        .containsEntry(McpMdcKeys.HANDLER_NAME, "mem://hello");
  }

  @Test
  void resource_template_kind_and_uri_template_render_as_handler_name() {
    var interceptor =
        new McpMdcInterceptor(HandlerKind.RESOURCE_TEMPLATE, "mem://item/{id}", "Fixtures");
    var captured = invokeCapturingMdc(interceptor);

    assertThat(captured.get())
        .containsEntry(McpMdcKeys.HANDLER_KIND, "resource_template")
        .containsEntry(McpMdcKeys.HANDLER_NAME, "mem://item/{id}");
  }

  private AtomicReference<Map<String, String>> invokeCapturingMdc(McpMdcInterceptor interceptor) {
    AtomicReference<Map<String, String>> captured = new AtomicReference<>();
    interceptor.intercept(
        MethodInvocation.of(
            dummyMethod(),
            new Fixtures(),
            null,
            new Object[0],
            () -> {
              var snapshot = MDC.getCopyOfContextMap();
              captured.set(snapshot == null ? Map.of() : snapshot);
              return null;
            }));
    return captured;
  }

  private static Method dummyMethod() {
    try {
      return Fixtures.class.getDeclaredMethod("target");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  static class Fixtures {
    /** Dummy target method used only to obtain a {@link Method} handle for test invocations. */
    public void target() {
      /* empty reflection target — see class javadoc. */
    }
  }
}
