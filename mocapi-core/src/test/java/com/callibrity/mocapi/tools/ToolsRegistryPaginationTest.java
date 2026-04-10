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

import com.callibrity.mocapi.util.Cursors;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ToolsRegistryPaginationTest {

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
    var registry = new ToolsRegistry(List.of(createProvider(3)), mapper, 10);
    var response = registry.listTools(null);

    assertThat(response.tools()).hasSize(3);
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldReturnFirstPageWithCursorWhenExceedsPageSize() {
    var registry = new ToolsRegistry(List.of(createProvider(5)), mapper, 2);
    var response = registry.listTools(null);

    assertThat(response.tools()).hasSize(2);
    assertThat(response.nextCursor()).isNotNull();
    assertThat(response.tools().get(0).name()).isEqualTo("tool-000");
    assertThat(response.tools().get(1).name()).isEqualTo("tool-001");
  }

  @Test
  void shouldReturnNextPageWithValidCursor() {
    var registry = new ToolsRegistry(List.of(createProvider(5)), mapper, 2);
    var firstPage = registry.listTools(null);

    var secondPage = registry.listTools(firstPage.nextCursor());
    assertThat(secondPage.tools()).hasSize(2);
    assertThat(secondPage.tools().get(0).name()).isEqualTo("tool-002");
    assertThat(secondPage.tools().get(1).name()).isEqualTo("tool-003");
    assertThat(secondPage.nextCursor()).isNotNull();
  }

  @Test
  void shouldReturnLastPageWithNullCursor() {
    var registry = new ToolsRegistry(List.of(createProvider(5)), mapper, 2);
    var firstPage = registry.listTools(null);
    var secondPage = registry.listTools(firstPage.nextCursor());
    var thirdPage = registry.listTools(secondPage.nextCursor());

    assertThat(thirdPage.tools()).hasSize(1);
    assertThat(thirdPage.tools().getFirst().name()).isEqualTo("tool-004");
    assertThat(thirdPage.nextCursor()).isNull();
  }

  @Test
  void shouldIterateThroughAllPages() {
    int totalTools = 7;
    int pageSize = 3;
    var registry = new ToolsRegistry(List.of(createProvider(totalTools)), mapper, pageSize);

    int totalRetrieved = 0;
    String cursor = null;
    do {
      var response = registry.listTools(cursor);
      totalRetrieved += response.tools().size();
      cursor = response.nextCursor();
    } while (cursor != null);

    assertThat(totalRetrieved).isEqualTo(totalTools);
  }

  @Test
  void shouldReturnExactPageWhenToolCountEqualsPageSize() {
    var registry = new ToolsRegistry(List.of(createProvider(3)), mapper, 3);
    var response = registry.listTools(null);

    assertThat(response.tools()).hasSize(3);
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldThrowOnInvalidCursor() {
    var registry = new ToolsRegistry(List.of(createProvider(3)), mapper, 2);

    assertThatThrownBy(() -> registry.listTools("not-valid-base64!!!"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void shouldClampOutOfRangeCursor() {
    var registry = new ToolsRegistry(List.of(createProvider(3)), mapper, 2);
    String cursor = Cursors.encode(100);

    var response = registry.listTools(cursor);
    assertThat(response.tools()).isEmpty();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldUseDefaultPageSizeOf50() {
    var registry = new ToolsRegistry(List.of(createProvider(60)), mapper);
    var response = registry.listTools(null);

    assertThat(response.tools()).hasSize(50);
    assertThat(response.nextCursor()).isNotNull();
  }

  @Test
  void cursorEncodingRoundTrips() {
    assertThat(Cursors.decode(Cursors.encode(42))).isEqualTo(42);
  }

  private static final class StubMcpTool implements McpTool {
    private final Descriptor descriptor;

    StubMcpTool(String name, ObjectNode schema) {
      this.descriptor = new Descriptor(name, name, name, schema, schema);
    }

    @Override
    public Descriptor descriptor() {
      return descriptor;
    }

    @Override
    public Object call(JsonNode arguments) {
      return arguments;
    }
  }
}
