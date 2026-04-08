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
package com.callibrity.mocapi.stream;

import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ElicitationCapability;
import com.callibrity.mocapi.session.LogLevel;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.substrate.core.Mailbox;
import org.jwcarman.substrate.core.MailboxFactory;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DefaultMcpStreamContextTest {

  private OdysseyStream stream;
  private ObjectMapper objectMapper;
  private MailboxFactory mailboxFactory;
  private SchemaGenerator schemaGenerator;
  private McpSessionService sessionService;

  @BeforeEach
  void setUp() {
    stream = mock(OdysseyStream.class);
    objectMapper = new ObjectMapper();
    mailboxFactory = mock(MailboxFactory.class);
    sessionService = mock(McpSessionService.class);
    schemaGenerator =
        new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(
                    objectMapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonSchemaModule())
                .with(new JakartaValidationModule())
                .build());
  }

  private DefaultMcpStreamContext createContext(String progressToken) {
    return createContext(progressToken, null);
  }

  private DefaultMcpStreamContext createContext(String progressToken, String sessionId) {
    return new DefaultMcpStreamContext(
        stream,
        objectMapper,
        progressToken,
        mailboxFactory,
        schemaGenerator,
        sessionService,
        sessionId,
        Duration.ofSeconds(5));
  }

  private McpSession sessionWithLogLevel(LogLevel level) {
    return new McpSession(
        "2025-11-25", new ClientCapabilities(null, null, null, null, null), null, level);
  }

  private void stubSessionWithLogLevel(String sessionId, LogLevel level) {
    when(sessionService.find(sessionId)).thenReturn(Optional.of(sessionWithLogLevel(level)));
  }

  @Test
  void sendProgressShouldPublishProgressNotificationWithToken() {
    var context = createContext("tok-123");
    context.sendProgress(5, 10);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("jsonrpc").asString()).isEqualTo(VERSION);
    assertThat(notification.get("method").asString()).isEqualTo("notifications/progress");
    assertThat(notification.get("params").get("progressToken").asString()).isEqualTo("tok-123");
    assertThat(notification.get("params").get("progress").asLong()).isEqualTo(5);
    assertThat(notification.get("params").get("total").asLong()).isEqualTo(10);
  }

  @Test
  void sendProgressShouldBeNoOpWithoutToken() {
    var context = createContext(null);
    context.sendProgress(5, 10);

    verify(stream, org.mockito.Mockito.never()).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void sendNotificationShouldPublishArbitraryNotification() {
    var context = createContext(null);
    context.sendNotification("custom/event", Map.of("key", "value"));

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("jsonrpc").asString()).isEqualTo(VERSION);
    assertThat(notification.get("method").asString()).isEqualTo("custom/event");
    assertThat(notification.get("params").get("key").asString()).isEqualTo("value");
  }

  @Test
  void sendNotificationShouldOmitParamsWhenNull() {
    var context = createContext(null);
    context.sendNotification("custom/event", null);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.has("params")).isFalse();
  }

  @Test
  void logWithLoggerShouldPublishMessageNotification() {
    stubSessionWithLogLevel("sess-1", LogLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.INFO, "my-tool", "Tool execution started");

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("jsonrpc").asString()).isEqualTo(VERSION);
    assertThat(notification.get("method").asString()).isEqualTo("notifications/message");
    assertThat(notification.get("params").get("level").asString()).isEqualTo("info");
    assertThat(notification.get("params").get("logger").asString()).isEqualTo("my-tool");
    assertThat(notification.get("params").get("data").asString())
        .isEqualTo("Tool execution started");
  }

  @Test
  void logWithoutLoggerShouldUseDefaultLogger() {
    stubSessionWithLogLevel("sess-1", LogLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.WARNING, "Something happened");

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("level").asString()).isEqualTo("warning");
    assertThat(notification.get("params").get("logger").asString()).isEqualTo("mcp");
    assertThat(notification.get("params").get("data").asString()).isEqualTo("Something happened");
  }

  @Test
  void logWithStructuredDataShouldSerializeAsJson() {
    stubSessionWithLogLevel("sess-1", LogLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.ERROR, "my-tool", Map.of("key", "value"));

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("data").get("key").asString()).isEqualTo("value");
  }

  @Test
  void logWithNullDataShouldSerializeAsNullNode() {
    stubSessionWithLogLevel("sess-1", LogLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.INFO, "my-tool", null);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("data").isNull()).isTrue();
  }

  @Test
  void logWithArrayDataShouldSerializeAsArrayNode() {
    stubSessionWithLogLevel("sess-1", LogLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.INFO, "my-tool", java.util.List.of("a", "b", "c"));

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    JsonNode dataNode = notification.get("params").get("data");
    assertThat(dataNode.isArray()).isTrue();
    assertThat(dataNode).hasSize(3);
    assertThat(dataNode.get(0).asString()).isEqualTo("a");
  }

  @Test
  void logBelowThresholdShouldBeDropped() {
    stubSessionWithLogLevel("sess-1", LogLevel.WARNING);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.DEBUG, "my-tool", "Should be dropped");

    verify(stream, org.mockito.Mockito.never()).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void logAtThresholdShouldBePublished() {
    stubSessionWithLogLevel("sess-1", LogLevel.WARNING);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.WARNING, "my-tool", "At threshold");

    verify(stream).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void logAboveThresholdShouldBePublished() {
    stubSessionWithLogLevel("sess-1", LogLevel.WARNING);
    var context = createContext(null, "sess-1");
    context.log(LogLevel.ERROR, "my-tool", "Above threshold");

    verify(stream).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void logWithNullSessionIdShouldDefaultToWarningThreshold() {
    var context = createContext(null, null);
    context.log(LogLevel.DEBUG, "my-tool", "Should be dropped");

    verify(stream, org.mockito.Mockito.never()).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void convenienceMethodsShouldDelegateToLog() {
    stubSessionWithLogLevel("sess-1", LogLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.debug("my-tool", "debug msg");
    context.info("my-tool", "info msg");
    context.notice("my-tool", "notice msg");
    context.warning("my-tool", "warning msg");
    context.error("my-tool", "error msg");
    context.critical("my-tool", "critical msg");
    context.alert("my-tool", "alert msg");
    context.emergency("my-tool", "emergency msg");

    verify(stream, org.mockito.Mockito.times(8)).publishJson(org.mockito.ArgumentMatchers.any());
  }

  // --- Elicitation tests ---

  record UserForm(String name, int age) {}

  private McpSession sessionWithElicitation() {
    return new McpSession(
        "2025-11-25",
        new ClientCapabilities(
            null,
            null,
            new ElicitationCapability(new ElicitationCapability.FormCapability(), null),
            null,
            null),
        null);
  }

  private McpSession sessionWithoutElicitation() {
    return new McpSession("2025-11-25", new ClientCapabilities(null, null, null, null, null), null);
  }

  private Mailbox<JsonNode> mockMailbox() {
    return (Mailbox<JsonNode>) (Mailbox<?>) mock(Mailbox.class);
  }

  @Test
  void elicitShouldThrowWhenClientDoesNotSupportElicitation() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithoutElicitation()));
    var context = createContext(null, "sess-1");
    assertThatThrownBy(() -> context.elicitForm("Enter info", UserForm.class))
        .isInstanceOf(McpElicitationNotSupportedException.class);
  }

  @Test
  void elicitShouldThrowWhenSessionIdIsNull() {
    var context = createContext(null, null);
    assertThatThrownBy(() -> context.elicitForm("Enter info", UserForm.class))
        .isInstanceOf(McpElicitationNotSupportedException.class);
  }

  @Test
  void elicitShouldPublishJsonRpcRequestAndBlockOnMailbox() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("name", "Alice", "age", 30)));
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(responseNode));

    ElicitationResult<UserForm> result = context.elicitForm("Enter your info", UserForm.class);

    assertThat(result.accepted()).isTrue();
    assertThat(result.content().name()).isEqualTo("Alice");
    assertThat(result.content().age()).isEqualTo(30);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());
    JsonNode request = captor.getValue();
    assertThat(request.get("jsonrpc").asString()).isEqualTo(VERSION);
    assertThat(request.get("method").asString()).isEqualTo("elicitation/create");
    assertThat(request.get("id")).isNotNull();
    assertThat(request.get("params").get("mode").asString()).isEqualTo("form");
    assertThat(request.get("params").get("message").asString()).isEqualTo("Enter your info");
    assertThat(request.get("params").get("requestedSchema")).isNotNull();
    assertThat(request.get("params").get("requestedSchema").get("type").asString())
        .isEqualTo("object");

    verify(mailbox).delete();
  }

  @Test
  void elicitShouldReturnDeclineWithNullContent() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);

    JsonNode responseNode = objectMapper.valueToTree(Map.of("action", "decline"));
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(responseNode));

    ElicitationResult<UserForm> result = context.elicitForm("Enter your info", UserForm.class);

    assertThat(result.declined()).isTrue();
    assertThat(result.content()).isNull();
    verify(mailbox).delete();
  }

  @Test
  void elicitShouldReturnCancelWithNullContent() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);

    JsonNode responseNode = objectMapper.valueToTree(Map.of("action", "cancel"));
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(responseNode));

    ElicitationResult<UserForm> result = context.elicitForm("Enter your info", UserForm.class);

    assertThat(result.cancelled()).isTrue();
    assertThat(result.content()).isNull();
    verify(mailbox).delete();
  }

  @Test
  void elicitShouldThrowOnTimeout() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> context.elicitForm("Enter info", UserForm.class))
        .isInstanceOf(McpElicitationTimeoutException.class);

    verify(mailbox).delete();
  }

  @Test
  void elicitShouldThrowOnInvalidContent() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("name", "Alice", "age", "not-a-number")));
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(responseNode));

    assertThatThrownBy(() -> context.elicitForm("Enter info", UserForm.class))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("schema validation");

    verify(mailbox).delete();
  }

  @Test
  void elicitShouldThrowOnJsonRpcError() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(Map.of("error", Map.of("code", -32600, "message", "Bad request")));
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(responseNode));

    assertThatThrownBy(() -> context.elicitForm("Enter info", UserForm.class))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("JSON-RPC error");

    verify(mailbox).delete();
  }

  @Test
  void elicitShouldCleanUpMailboxEvenOnException() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonNode> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonNode.class))).thenReturn(mailbox);
    when(mailbox.poll(any(Duration.class))).thenThrow(new RuntimeException("Unexpected"));

    assertThatThrownBy(() -> context.elicitForm("Enter info", UserForm.class))
        .isInstanceOf(RuntimeException.class);

    verify(mailbox).delete();
  }
}
