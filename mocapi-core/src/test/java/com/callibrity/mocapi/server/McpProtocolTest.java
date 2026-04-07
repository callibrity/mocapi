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
package com.callibrity.mocapi.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.server.exception.McpInvalidParamsException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class McpProtocolTest {

  private McpProtocol protocol;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));
    JsonRpcMessages messages = new JsonRpcMessages(objectMapper);
    McpMethodRegistry registry =
        McpMethodRegistry.builder()
            .register("ping", new McpMethodHandler.Json(_ -> "pong"))
            .register(
                "fail",
                new McpMethodHandler.Json(
                    _ -> {
                      throw new RuntimeException("boom");
                    }))
            .register(
                "mcp-fail",
                new McpMethodHandler.Json(
                    _ -> {
                      throw new McpInvalidParamsException("bad params");
                    }))
            .register("stream-echo", new McpMethodHandler.Streaming((params, _) -> params))
            .build();
    protocol = new McpProtocol(validator, registry, messages);
  }

  @Nested
  class JsonDispatch {

    @Test
    void shouldDispatchToJsonHandler() {
      var result = protocol.dispatch("ping", null, objectMapper.valueToTree(1));
      assertThat(result).isInstanceOf(McpProtocol.DispatchResult.JsonResult.class);
      ObjectNode response = ((McpProtocol.DispatchResult.JsonResult) result).response();
      assertThat(response.get("result").asString()).isEqualTo("pong");
    }

    @Test
    void shouldReturnMethodNotFound() {
      var result = protocol.dispatch("nonexistent", null, objectMapper.valueToTree(1));
      assertThat(result).isInstanceOf(McpProtocol.DispatchResult.JsonResult.class);
      ObjectNode response = ((McpProtocol.DispatchResult.JsonResult) result).response();
      assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void shouldHandleRuntimeException() {
      var result = protocol.dispatch("fail", null, objectMapper.valueToTree(1));
      assertThat(result).isInstanceOf(McpProtocol.DispatchResult.JsonResult.class);
      ObjectNode response = ((McpProtocol.DispatchResult.JsonResult) result).response();
      assertThat(response.get("error").get("code").asInt()).isEqualTo(-32603);
    }

    @Test
    void shouldHandleMcpException() {
      var result = protocol.dispatch("mcp-fail", null, objectMapper.valueToTree(1));
      assertThat(result).isInstanceOf(McpProtocol.DispatchResult.JsonResult.class);
      ObjectNode response = ((McpProtocol.DispatchResult.JsonResult) result).response();
      assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
    }
  }

  @Nested
  class StreamingDispatch {

    @Test
    void shouldReturnStreamingDispatchForStreamingHandler() {
      var result = protocol.dispatch("stream-echo", null, objectMapper.valueToTree(1));
      assertThat(result).isInstanceOf(McpProtocol.DispatchResult.StreamingDispatch.class);
    }
  }

  @Nested
  class ExecuteStreaming {

    @Test
    void shouldExecuteStreamingHandler() {
      McpStreamContext context = mock(McpStreamContext.class);
      ObjectNode params = objectMapper.createObjectNode();
      params.put("value", "hello");

      ObjectNode response =
          protocol.executeStreaming(
              (p, _) -> p, context, "stream-echo", params, objectMapper.valueToTree(1));

      assertThat(response.get("jsonrpc").asString()).isEqualTo("2.0");
      assertThat(response.get("result").get("value").asString()).isEqualTo("hello");
    }

    @Test
    void shouldHandleStreamingRuntimeException() {
      McpStreamContext context = mock(McpStreamContext.class);
      ObjectNode response =
          protocol.executeStreaming(
              (_, _) -> {
                throw new RuntimeException("boom");
              },
              context,
              "fail",
              null,
              objectMapper.valueToTree(1));

      assertThat(response.get("error").get("code").asInt()).isEqualTo(-32603);
    }

    @Test
    void shouldHandleStreamingMcpException() {
      McpStreamContext context = mock(McpStreamContext.class);
      ObjectNode response =
          protocol.executeStreaming(
              (_, _) -> {
                throw new McpInvalidParamsException("bad");
              },
              context,
              "mcp-fail",
              null,
              objectMapper.valueToTree(1));

      assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
    }
  }
}
