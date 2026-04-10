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

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.SamplingCapability;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStream;
import com.callibrity.mocapi.stream.elicitation.McpElicitationException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationTimeoutException;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.core.Mailbox;
import org.jwcarman.substrate.core.MailboxFactory;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import tools.jackson.databind.node.ValueNode;

class DefaultMcpStreamContextTest {

  private McpSessionStream stream;
  private ObjectMapper objectMapper;
  private MailboxFactory mailboxFactory;
  private McpSessionService sessionService;

  @BeforeEach
  void setUp() {
    stream = mock(McpSessionStream.class);
    objectMapper = new ObjectMapper();
    mailboxFactory = mock(MailboxFactory.class);
    sessionService = mock(McpSessionService.class);
  }

  private DefaultMcpStreamContext<?> createContext(String progressToken) {
    return createContext(progressToken, null);
  }

  private DefaultMcpStreamContext<?> createContext(String progressToken, String sessionId) {
    ValueNode tokenNode = progressToken == null ? null : StringNode.valueOf(progressToken);
    return new DefaultMcpStreamContext<>(
        stream,
        new DefaultMcpStreamContext.Dependencies(
            objectMapper,
            mailboxFactory,
            sessionService,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5)),
        tokenNode,
        sessionId,
        null);
  }

  private DefaultMcpStreamContext<?> createContextWithTimeouts(
      String sessionId, Duration elicitationTimeout, Duration samplingTimeout) {
    return new DefaultMcpStreamContext<>(
        stream,
        new DefaultMcpStreamContext.Dependencies(
            objectMapper, mailboxFactory, sessionService, elicitationTimeout, samplingTimeout),
        null,
        sessionId,
        null);
  }

  private McpSession sessionWithLogLevel(LoggingLevel level) {
    return new McpSession("2025-11-25", new ClientCapabilities(null, null, null), null, level);
  }

  private void stubSessionWithLogLevel(String sessionId, LoggingLevel level) {
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
    stubSessionWithLogLevel("sess-1", LoggingLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.INFO, "my-tool", "Tool execution started");

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
    stubSessionWithLogLevel("sess-1", LoggingLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.WARNING, "Something happened");

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("level").asString()).isEqualTo("warning");
    assertThat(notification.get("params").get("logger").asString()).isEqualTo("mcp");
    assertThat(notification.get("params").get("data").asString()).isEqualTo("Something happened");
  }

  @Test
  void logWithStructuredDataShouldSerializeAsJson() {
    stubSessionWithLogLevel("sess-1", LoggingLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.ERROR, "my-tool", Map.of("key", "value"));

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("data").get("key").asString()).isEqualTo("value");
  }

  @Test
  void logWithNullDataShouldSerializeAsNullNode() {
    stubSessionWithLogLevel("sess-1", LoggingLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.INFO, "my-tool", null);

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());

    JsonNode notification = captor.getValue();
    assertThat(notification.get("params").get("data").isNull()).isTrue();
  }

  @Test
  void logWithArrayDataShouldSerializeAsArrayNode() {
    stubSessionWithLogLevel("sess-1", LoggingLevel.DEBUG);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.INFO, "my-tool", java.util.List.of("a", "b", "c"));

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
    stubSessionWithLogLevel("sess-1", LoggingLevel.WARNING);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.DEBUG, "my-tool", "Should be dropped");

    verify(stream, org.mockito.Mockito.never()).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void logAtThresholdShouldBePublished() {
    stubSessionWithLogLevel("sess-1", LoggingLevel.WARNING);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.WARNING, "my-tool", "At threshold");

    verify(stream).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void logAboveThresholdShouldBePublished() {
    stubSessionWithLogLevel("sess-1", LoggingLevel.WARNING);
    var context = createContext(null, "sess-1");
    context.log(LoggingLevel.ERROR, "my-tool", "Above threshold");

    verify(stream).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void logWithNullSessionIdShouldDefaultToWarningThreshold() {
    var context = createContext(null, null);
    context.log(LoggingLevel.DEBUG, "my-tool", "Should be dropped");

    verify(stream, org.mockito.Mockito.never()).publishJson(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void convenienceMethodsShouldDelegateToLog() {
    stubSessionWithLogLevel("sess-1", LoggingLevel.DEBUG);
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

  private McpSession sessionWithElicitation() {
    return new McpSession(
        "2025-11-25", new ClientCapabilities(null, null, new ElicitationCapability()), null);
  }

  private McpSession sessionWithoutElicitation() {
    return new McpSession("2025-11-25", new ClientCapabilities(null, null, null), null);
  }

  private Mailbox<JsonRpcResponse> mockMailbox() {
    return (Mailbox<JsonRpcResponse>) (Mailbox<?>) mock(Mailbox.class);
  }

  // --- Builder-based elicit tests ---

  @Test
  void elicitWithBuilderShouldPublishRequestAndReturnJsonNode() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("username", "Alice", "email", "a@b.com")));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    ElicitResult result =
        context.elicit(
            "Enter your info",
            schema -> schema.string("username", "User's name").string("email", "User's email"));

    assertThat(result.isAccepted()).isTrue();
    assertThat(result.getString("username")).isEqualTo("Alice");
    assertThat(result.getString("email")).isEqualTo("a@b.com");

    ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
    verify(stream).publishJson(captor.capture());
    JsonNode request = captor.getValue();
    assertThat(request.get("method").asString()).isEqualTo("elicitation/create");
    assertThat(request.get("params").get("requestedSchema").get("type").asString())
        .isEqualTo("object");
    assertThat(request.get("params").get("requestedSchema").get("required")).hasSize(2);

    verify(mailbox).delete();
  }

  @Test
  void elicitWithBuilderShouldReturnDecline() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode = objectMapper.valueToTree(Map.of("action", "decline"));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    ElicitResult result = context.elicit("Enter info", schema -> schema.string("name", "Name"));

    assertThat(result.isAccepted()).isFalse();
    verify(mailbox).delete();
  }

  @Test
  void elicitWithBuilderShouldThrowWhenElicitationNotSupported() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithoutElicitation()));
    var context = createContext(null, "sess-1");

    assertThatThrownBy(() -> context.elicit("Enter info", schema -> schema.string("name", "Name")))
        .isInstanceOf(McpElicitationNotSupportedException.class);
  }

  @Test
  void elicitWithBuilderShouldThrowOnTimeout() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> context.elicit("Enter info", schema -> schema.string("name", "Name")))
        .isInstanceOf(McpElicitationTimeoutException.class);

    verify(mailbox).delete();
  }

  @Test
  void elicitWithBuilderShouldValidateContent() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("age", "not-an-integer")));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    assertThatThrownBy(() -> context.elicit("Enter info", schema -> schema.integer("age", "Age")))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("schema validation");

    verify(mailbox).delete();
  }

  @Test
  void elicitShouldThrowOnJsonRpcError() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonRpcError rpcError = new JsonRpcError(-32600, "Invalid Request", null);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(rpcError));

    assertThatThrownBy(() -> context.elicit("Enter info", schema -> schema.string("name", "Name")))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("JSON-RPC error");

    verify(mailbox).delete();
  }

  // --- Bean-oriented elicit overload tests ---

  record UserForm(String name, int age) {}

  @Test
  void elicitWithClassShouldDeserializeAcceptedContent() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("name", "Alice", "age", 30)));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    Optional<UserForm> result =
        context.elicit(
            "Enter info",
            schema -> schema.string("name", "Name").integer("age", "Age"),
            UserForm.class);

    assertThat(result).contains(new UserForm("Alice", 30));
    verify(mailbox).delete();
  }

  @Test
  void elicitWithTypeReferenceShouldDeserializeAcceptedContent() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("name", "Alice", "email", "a@b.com")));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    Optional<Map<String, String>> result =
        context.elicit(
            "Enter info",
            schema -> schema.string("name", "Name").string("email", "Email"),
            new TypeReference<Map<String, String>>() {});

    assertThat(result).isPresent();
    assertThat(result.get()).containsEntry("name", "Alice").containsEntry("email", "a@b.com");
    verify(mailbox).delete();
  }

  @Test
  void elicitWithClassShouldReturnEmptyOnDecline() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode = objectMapper.valueToTree(Map.of("action", "decline"));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    Optional<UserForm> result =
        context.elicit("Enter info", schema -> schema.string("name", "Name"), UserForm.class);

    assertThat(result).isEmpty();
    verify(mailbox).delete();
  }

  @Test
  void elicitWithClassShouldReturnEmptyOnCancel() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode = objectMapper.valueToTree(Map.of("action", "cancel"));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    Optional<UserForm> result =
        context.elicit("Enter info", schema -> schema.string("name", "Name"), UserForm.class);

    assertThat(result).isEmpty();
    verify(mailbox).delete();
  }

  @Test
  void elicitWithClassShouldThrowOnSchemaValidationFailure() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("age", "not-an-integer")));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    assertThatThrownBy(
            () ->
                context.elicit(
                    "Enter info", schema -> schema.integer("age", "Age"), UserForm.class))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("schema validation");

    verify(mailbox).delete();
  }

  @Test
  void elicitWithClassShouldThrowDatabindExceptionOnMismatchedBean() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode responseNode =
        objectMapper.valueToTree(
            Map.of("action", "accept", "content", Map.of("totally", "wrong", "fields", "here")));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(responseNode, null)));

    assertThatThrownBy(
            () ->
                context.elicit(
                    "Enter info",
                    schema -> schema.string("totally", "T").string("fields", "F"),
                    UserForm.class))
        .isInstanceOf(DatabindException.class);

    verify(mailbox).delete();
  }

  // --- Sampling tests ---

  private McpSession sessionWithSampling() {
    return new McpSession(
        "2025-11-25", new ClientCapabilities(null, new SamplingCapability(), null), null);
  }

  @Test
  void sampleShouldReturnResultOnSuccess() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithSampling()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonNode resultPayload =
        objectMapper.valueToTree(
            Map.of("role", "assistant", "content", Map.of("type", "text", "text", "Hello")));
    when(mailbox.poll(any(Duration.class)))
        .thenReturn(Optional.of(new JsonRpcResult(resultPayload, null)));

    CreateMessageResult result = context.sample("Say hello", 100);

    assertThat(result.role()).isEqualTo(Role.ASSISTANT);
    assertThat(result.text()).isEqualTo("Hello");
    verify(mailbox).delete();
  }

  @Test
  void sampleShouldThrowOnJsonRpcError() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithSampling()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);

    JsonRpcError rpcError = new JsonRpcError(-32600, "Invalid Request", null);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.of(rpcError));

    assertThatThrownBy(() -> context.sample("Say hello", 100))
        .isInstanceOf(McpSamplingException.class)
        .hasMessageContaining("JSON-RPC error");

    verify(mailbox).delete();
  }

  @Test
  void sampleShouldThrowOnTimeout() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithSampling()));
    var context = createContext(null, "sess-1");
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> context.sample("Say hello", 100))
        .isInstanceOf(McpSamplingTimeoutException.class);

    verify(mailbox).delete();
  }

  @Test
  void sampleShouldThrowWhenSamplingNotSupported() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithoutElicitation()));
    var context = createContext(null, "sess-1");

    assertThatThrownBy(() -> context.sample("Say hello", 100))
        .isInstanceOf(McpSamplingNotSupportedException.class);
  }

  // --- Distinct timeout tests ---

  @Test
  void sampleShouldUseSamplingTimeout() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithSampling()));
    Duration samplingTimeout = Duration.ofMillis(100);
    Duration elicitationTimeout = Duration.ofMinutes(5);
    var context = createContextWithTimeouts("sess-1", elicitationTimeout, samplingTimeout);
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> context.sample("Say hello", 100))
        .isInstanceOf(McpSamplingTimeoutException.class);

    verify(mailbox).poll(samplingTimeout);
    verify(mailbox).delete();
  }

  @Test
  void elicitShouldUseElicitationTimeout() {
    when(sessionService.find("sess-1")).thenReturn(Optional.of(sessionWithElicitation()));
    Duration samplingTimeout = Duration.ofMillis(100);
    Duration elicitationTimeout = Duration.ofMinutes(5);
    var context = createContextWithTimeouts("sess-1", elicitationTimeout, samplingTimeout);
    Mailbox<JsonRpcResponse> mailbox = mockMailbox();
    when(mailboxFactory.create(any(String.class), eq(JsonRpcResponse.class))).thenReturn(mailbox);
    when(mailbox.poll(any(Duration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> context.elicit("Enter info", schema -> schema.string("name", "Name")))
        .isInstanceOf(McpElicitationTimeoutException.class);

    verify(mailbox).poll(elicitationTimeout);
    verify(mailbox).delete();
  }
}
