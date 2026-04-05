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
package com.callibrity.mocapi.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.server.exception.McpInvalidParamsException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class McpToolsCapabilityPaginationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private McpToolProvider createProvider(int toolCount) {
    ObjectNode emptySchema = mapper.createObjectNode().put("type", "object");
    List<McpTool> tools =
        IntStream.range(0, toolCount)
            .mapToObj(
                i -> {
                  String name = String.format("tool-%03d", i);
                  return new StubMcpTool(name, emptySchema);
                })
            .map(McpTool.class::cast)
            .toList();
    return () -> tools;
  }

  @Test
  void shouldReturnAllToolsInSinglePageWhenWithinPageSize() {
    var capability = new McpToolsCapability(List.of(createProvider(3)), 10);
    var response = capability.listTools(null);

    assertThat(response.tools()).hasSize(3);
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldReturnFirstPageWithCursorWhenExceedsPageSize() {
    var capability = new McpToolsCapability(List.of(createProvider(5)), 2);
    var response = capability.listTools(null);

    assertThat(response.tools()).hasSize(2);
    assertThat(response.nextCursor()).isNotNull();
    assertThat(response.tools().get(0).name()).isEqualTo("tool-000");
    assertThat(response.tools().get(1).name()).isEqualTo("tool-001");
  }

  @Test
  void shouldReturnNextPageWithValidCursor() {
    var capability = new McpToolsCapability(List.of(createProvider(5)), 2);
    var firstPage = capability.listTools(null);

    var secondPage = capability.listTools(firstPage.nextCursor());
    assertThat(secondPage.tools()).hasSize(2);
    assertThat(secondPage.tools().get(0).name()).isEqualTo("tool-002");
    assertThat(secondPage.tools().get(1).name()).isEqualTo("tool-003");
    assertThat(secondPage.nextCursor()).isNotNull();
  }

  @Test
  void shouldReturnLastPageWithNullCursor() {
    var capability = new McpToolsCapability(List.of(createProvider(5)), 2);
    var firstPage = capability.listTools(null);
    var secondPage = capability.listTools(firstPage.nextCursor());
    var thirdPage = capability.listTools(secondPage.nextCursor());

    assertThat(thirdPage.tools()).hasSize(1);
    assertThat(thirdPage.tools().getFirst().name()).isEqualTo("tool-004");
    assertThat(thirdPage.nextCursor()).isNull();
  }

  @Test
  void shouldIterateThroughAllPages() {
    int totalTools = 7;
    int pageSize = 3;
    var capability = new McpToolsCapability(List.of(createProvider(totalTools)), pageSize);

    int totalRetrieved = 0;
    String cursor = null;
    do {
      var response = capability.listTools(cursor);
      totalRetrieved += response.tools().size();
      cursor = response.nextCursor();
    } while (cursor != null);

    assertThat(totalRetrieved).isEqualTo(totalTools);
  }

  @Test
  void shouldReturnExactPageWhenToolCountEqualsPageSize() {
    var capability = new McpToolsCapability(List.of(createProvider(3)), 3);
    var response = capability.listTools(null);

    assertThat(response.tools()).hasSize(3);
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldThrowOnInvalidCursor() {
    var capability = new McpToolsCapability(List.of(createProvider(3)), 2);

    assertThatThrownBy(() -> capability.listTools("not-valid-base64!!!"))
        .isExactlyInstanceOf(McpInvalidParamsException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void shouldThrowOnOutOfRangeCursor() {
    var capability = new McpToolsCapability(List.of(createProvider(3)), 2);
    String cursor = McpToolsCapability.encodeCursor(100);

    assertThatThrownBy(() -> capability.listTools(cursor))
        .isExactlyInstanceOf(McpInvalidParamsException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void shouldUseDefaultPageSizeOf50() {
    var capability = new McpToolsCapability(List.of(createProvider(60)));
    var response = capability.listTools(null);

    assertThat(response.tools()).hasSize(50);
    assertThat(response.nextCursor()).isNotNull();
  }

  @Test
  void cursorEncodingRoundTrips() {
    assertThat(McpToolsCapability.decodeCursor(McpToolsCapability.encodeCursor(42))).isEqualTo(42);
  }

  private record StubMcpTool(String name, ObjectNode schema) implements McpTool {
    @Override
    public String title() {
      return name;
    }

    @Override
    public String description() {
      return name;
    }

    @Override
    public ObjectNode inputSchema() {
      return schema;
    }

    @Override
    public ObjectNode outputSchema() {
      return schema;
    }

    @Override
    public ObjectNode call(ObjectNode parameters) {
      return parameters;
    }
  }
}
