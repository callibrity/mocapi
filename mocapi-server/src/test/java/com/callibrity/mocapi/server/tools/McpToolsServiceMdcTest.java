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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolProvider;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.observability.McpMdcKeys;
import com.callibrity.mocapi.server.session.McpSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolsServiceMdcTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private McpResponseCorrelationService correlationService;

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  private McpTool capturingTool(String name, Map<String, String> capture) {
    return new McpTool() {
      @Override
      public Tool descriptor() {
        return new Tool(name, null, null, mapper.createObjectNode().put("type", "object"), null);
      }

      @Override
      public Object call(JsonNode args) {
        capture.put(McpMdcKeys.SESSION, MDC.get(McpMdcKeys.SESSION));
        capture.put(McpMdcKeys.HANDLER_KIND, MDC.get(McpMdcKeys.HANDLER_KIND));
        capture.put(McpMdcKeys.HANDLER_NAME, MDC.get(McpMdcKeys.HANDLER_NAME));
        return "ok";
      }
    };
  }

  private McpTool throwingTool(String name) {
    return new McpTool() {
      @Override
      public Tool descriptor() {
        return new Tool(name, null, null, mapper.createObjectNode().put("type", "object"), null);
      }

      @Override
      public Object call(JsonNode args) {
        throw new IllegalStateException("boom");
      }
    };
  }

  private McpToolsService serviceFor(McpTool... tools) {
    McpToolProvider provider = () -> List.of(tools);
    return new McpToolsService(List.of(provider), mapper, correlationService);
  }

  @Test
  void mdc_is_populated_during_invocation_and_cleared_after() {
    var captured = new java.util.HashMap<String, String>();
    var service = serviceFor(capturingTool("inspect", captured));

    var session =
        new McpSession(
            "sess-xyz",
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("client", "v", "1"));
    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> service.callTool(new CallToolRequestParams("inspect", null, null, null)));

    assertThat(captured)
        .containsEntry(McpMdcKeys.SESSION, "sess-xyz")
        .containsEntry(McpMdcKeys.HANDLER_KIND, McpMdcKeys.KIND_TOOL)
        .containsEntry(McpMdcKeys.HANDLER_NAME, "inspect");
    assertMdcEmpty();
  }

  @Test
  void mdc_is_cleared_after_tool_throws() {
    var service = serviceFor(throwingTool("bad"));

    var result = service.callTool(new CallToolRequestParams("bad", null, null, null));

    assertThat(result.isError()).isTrue();
    assertMdcEmpty();
  }

  private static void assertMdcEmpty() {
    var map = MDC.getCopyOfContextMap();
    assertThat(map == null || map.isEmpty()).as("MDC should be empty, was %s", map).isTrue();
  }
}
