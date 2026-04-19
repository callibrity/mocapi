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

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.server.session.McpSession;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.intercept.MethodInvocation;
import org.slf4j.MDC;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpMdcInterceptorTest {

  private final McpMdcInterceptor interceptor = new McpMdcInterceptor();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void sets_tool_keys_for_the_duration_of_proceed() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("toolMethod");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("tool");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("my-tool");
    assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isNull();
    assertThat(MDC.get(McpMdcKeys.HANDLER_NAME)).isNull();
  }

  @Test
  void sets_prompt_keys() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("promptMethod");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("prompt");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("my-prompt");
  }

  @Test
  void sets_resource_keys() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("resourceMethod");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("resource");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("mem://hello");
  }

  @Test
  void sets_resource_template_keys() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("resourceTemplateMethod");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("resource_template");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("mem://item/{id}");
  }

  @Test
  void sets_session_key_when_session_is_bound() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("toolMethod");
    var session = new McpSession("session-42", "2025-11-25", null, null);
    AtomicReference<String> seen = new AtomicReference<>();

    ScopedValue.where(McpSession.CURRENT, session)
        .run(
            () -> {
              try {
                interceptor.intercept(
                    MethodInvocation.of(
                        method,
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
  void omits_session_key_when_session_is_not_bound() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("toolMethod");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().containsKey(McpMdcKeys.SESSION)).isFalse();
  }

  @Test
  void removes_keys_even_when_proceed_throws() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("toolMethod");
    var invocation =
        MethodInvocation.of(
            method,
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
  void preserves_pre_existing_mdc_entries() throws Exception {
    MDC.put("upstream.trace", "abc-123");
    var method = Fixtures.class.getDeclaredMethod("toolMethod");

    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().get("upstream.trace")).isEqualTo("abc-123");
    assertThat(MDC.get("upstream.trace")).isEqualTo("abc-123");
  }

  @Test
  void does_not_stamp_keys_when_method_lacks_mcp_annotation() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("plainMethod");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().containsKey(McpMdcKeys.HANDLER_KIND)).isFalse();
    assertThat(captured.get().containsKey(McpMdcKeys.HANDLER_NAME)).isFalse();
  }

  @Test
  void does_not_stamp_name_key_when_annotation_name_is_blank() throws Exception {
    var method = Fixtures.class.getDeclaredMethod("toolWithDefaultName");
    var captured = invokeCapturingMdc(method);

    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("tool");
    assertThat(captured.get().containsKey(McpMdcKeys.HANDLER_NAME)).isFalse();
  }

  private AtomicReference<Map<String, String>> invokeCapturingMdc(Method method) {
    AtomicReference<Map<String, String>> captured = new AtomicReference<>();
    interceptor.intercept(
        MethodInvocation.of(
            method,
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

  static class Fixtures {
    @McpTool(name = "my-tool")
    public void toolMethod() {}

    @McpTool
    public void toolWithDefaultName() {}

    @McpPrompt(name = "my-prompt")
    public void promptMethod() {}

    @McpResource(uri = "mem://hello")
    public void resourceMethod() {}

    @McpResourceTemplate(uriTemplate = "mem://item/{id}")
    public void resourceTemplateMethod() {}

    public void plainMethod() {}
  }
}
