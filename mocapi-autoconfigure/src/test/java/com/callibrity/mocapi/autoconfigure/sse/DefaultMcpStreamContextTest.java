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
package com.callibrity.mocapi.autoconfigure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DefaultMcpStreamContextTest {

  private OdysseyStream stream;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    stream = mock(OdysseyStream.class);
    objectMapper = new ObjectMapper();
  }

  @Test
  void sendProgressShouldPublishProgressNotificationWithToken() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, "tok-123");
    context.sendProgress(5, 10);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("jsonrpc").asString()).isEqualTo("2.0");
    assertThat(notification.get("method").asString()).isEqualTo("notifications/progress");
    assertThat(notification.get("params").get("progressToken").asString()).isEqualTo("tok-123");
    assertThat(notification.get("params").get("progress").asLong()).isEqualTo(5);
    assertThat(notification.get("params").get("total").asLong()).isEqualTo(10);
  }

  @Test
  void sendProgressShouldBeNoOpWithoutToken() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, null);
    context.sendProgress(5, 10);

    verify(stream, org.mockito.Mockito.never()).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void sendNotificationShouldPublishArbitraryNotification() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, null);
    context.sendNotification("custom/event", Map.of("key", "value"));

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("jsonrpc").asString()).isEqualTo("2.0");
    assertThat(notification.get("method").asString()).isEqualTo("custom/event");
    assertThat(notification.get("params").get("key").asString()).isEqualTo("value");
  }

  @Test
  void sendNotificationShouldOmitParamsWhenNull() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, null);
    context.sendNotification("custom/event", null);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.has("params")).isFalse();
  }

  @Test
  void logWithLoggerShouldPublishMessageNotification() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, null);
    context.log("info", "my-tool", "Tool execution started");

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("jsonrpc").asString()).isEqualTo("2.0");
    assertThat(notification.get("method").asString()).isEqualTo("notifications/message");
    assertThat(notification.get("params").get("level").asString()).isEqualTo("info");
    assertThat(notification.get("params").get("logger").asString()).isEqualTo("my-tool");
    assertThat(notification.get("params").get("data").asString())
        .isEqualTo("Tool execution started");
  }

  @Test
  void logWithoutLoggerShouldUseDefaultLogger() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, null);
    context.log("warning", "Something happened");

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("level").asString()).isEqualTo("warning");
    assertThat(notification.get("params").get("logger").asString()).isEqualTo("mcp");
    assertThat(notification.get("params").get("data").asString()).isEqualTo("Something happened");
  }

  @Test
  void logWithStructuredDataShouldSerializeAsJson() {
    var context = new DefaultMcpStreamContext(stream, objectMapper, null);
    context.log("debug", "my-tool", Map.of("key", "value"));

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("data").get("key").asString()).isEqualTo("value");
  }
}
