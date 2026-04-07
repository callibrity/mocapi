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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class JsonRpcMessagesTest {

  private JsonRpcMessages messages;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    messages = new JsonRpcMessages(objectMapper);
  }

  @Test
  void successResponseShouldIncludeJsonRpcVersion() {
    ObjectNode response = messages.successResponse(objectMapper.valueToTree(1), "hello");
    assertThat(response.get("jsonrpc").asString()).isEqualTo("2.0");
  }

  @Test
  void successResponseShouldIncludeId() {
    ObjectNode response = messages.successResponse(objectMapper.valueToTree(42), "hello");
    assertThat(response.get("id").asInt()).isEqualTo(42);
  }

  @Test
  void successResponseShouldIncludeResult() {
    ObjectNode response = messages.successResponse(objectMapper.valueToTree(1), "hello");
    assertThat(response.get("result").asString()).isEqualTo("hello");
  }

  @Test
  void successResponseShouldOmitNullId() {
    ObjectNode response = messages.successResponse(null, "hello");
    assertThat(response.has("id")).isFalse();
  }

  @Test
  void successResponseShouldOmitNullResult() {
    ObjectNode response = messages.successResponse(objectMapper.valueToTree(1), null);
    assertThat(response.has("result")).isFalse();
  }

  @Test
  void errorResponseShouldIncludeJsonRpcVersion() {
    ObjectNode response = messages.errorResponse(objectMapper.valueToTree(1), -32600, "bad");
    assertThat(response.get("jsonrpc").asString()).isEqualTo("2.0");
  }

  @Test
  void errorResponseShouldIncludeErrorCodeAndMessage() {
    ObjectNode response = messages.errorResponse(objectMapper.valueToTree(1), -32600, "bad");
    assertThat(response.get("error").get("code").asInt()).isEqualTo(-32600);
    assertThat(response.get("error").get("message").asString()).isEqualTo("bad");
  }

  @Test
  void errorResponseShouldOmitNullId() {
    ObjectNode response = messages.errorResponse(null, -32600, "bad");
    assertThat(response.has("id")).isFalse();
  }
}
