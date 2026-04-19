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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.session.McpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class McpMdcScopeTest {

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void pushSetsEveryNonNullKey() {
    McpSession session =
        new McpSession(
            "sess-1",
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("c", "v", "1"));
    ScopedValue.where(McpSession.CURRENT, session)
        .run(
            () -> {
              try (var ignored = McpMdcScope.push(McpMdcKeys.KIND_TOOL, "my-tool", "req-42")) {
                assertThat(MDC.get(McpMdcKeys.SESSION)).isEqualTo("sess-1");
                assertThat(MDC.get(McpMdcKeys.REQUEST)).isEqualTo("req-42");
                assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isEqualTo(McpMdcKeys.KIND_TOOL);
                assertThat(MDC.get(McpMdcKeys.HANDLER_NAME)).isEqualTo("my-tool");
              }
            });
  }

  @Test
  void closeRemovesOnlyKeysItAdded() {
    MDC.put("unrelated", "keep-me");
    MDC.put(McpMdcKeys.SESSION, "pre-existing-session");
    try (var ignored = McpMdcScope.push(McpMdcKeys.KIND_PROMPT, "hello", null)) {
      assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isEqualTo(McpMdcKeys.KIND_PROMPT);
    }
    assertThat(MDC.get("unrelated")).isEqualTo("keep-me");
    assertThat(MDC.get(McpMdcKeys.SESSION)).isEqualTo("pre-existing-session");
    assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isNull();
    assertThat(MDC.get(McpMdcKeys.HANDLER_NAME)).isNull();
  }

  @Test
  void nullRequestIdMeansRequestKeyIsNotSet() {
    try (var ignored = McpMdcScope.push(McpMdcKeys.KIND_TOOL, "t", null)) {
      assertThat(MDC.get(McpMdcKeys.REQUEST)).isNull();
    }
  }

  @Test
  void sessionNotBoundMeansSessionKeyIsNotSet() {
    try (var ignored = McpMdcScope.push(McpMdcKeys.KIND_TOOL, "t", "req-1")) {
      assertThat(MDC.get(McpMdcKeys.SESSION)).isNull();
      assertThat(MDC.get(McpMdcKeys.REQUEST)).isEqualTo("req-1");
    }
  }
}
