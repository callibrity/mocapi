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

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.LoggingMessageNotificationParams;
import com.callibrity.mocapi.model.ProgressNotification;
import com.callibrity.mocapi.model.ProgressNotificationParams;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.SamplingMessage;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStream;
import com.callibrity.mocapi.stream.elicitation.McpElicitationException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationTimeoutException;
import com.callibrity.mocapi.stream.elicitation.RequestedSchemaBuilder;
import com.callibrity.mocapi.tools.ToolsRegistry;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jwcarman.substrate.core.Mailbox;
import org.jwcarman.substrate.core.MailboxFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/**
 * Default implementation of {@link McpStreamContext} that wraps an {@link McpSessionStream} with
 * MCP-specific JSON-RPC notification formatting and elicitation support via Substrate Mailbox.
 */
public class DefaultMcpStreamContext<R> implements McpStreamContext<R> {

  public record Dependencies(
      ObjectMapper objectMapper,
      MailboxFactory mailboxFactory,
      McpSessionService sessionService,
      Duration elicitationTimeout,
      Duration samplingTimeout) {}

  private final McpSessionStream stream;
  private final ObjectMapper objectMapper;
  private final ValueNode progressToken;
  private final MailboxFactory mailboxFactory;
  private final McpSessionService sessionService;
  private final String sessionId;
  private final Duration elicitationTimeout;
  private final Duration samplingTimeout;

  private final JsonNode requestId;
  private final AtomicBoolean resultSent = new AtomicBoolean(false);

  public DefaultMcpStreamContext(
      McpSessionStream stream,
      Dependencies deps,
      ValueNode progressToken,
      String sessionId,
      JsonNode requestId) {
    this.stream = stream;
    this.objectMapper = deps.objectMapper();
    this.progressToken = progressToken;
    this.mailboxFactory = deps.mailboxFactory();
    this.sessionService = deps.sessionService();
    this.sessionId = sessionId;
    this.elicitationTimeout = deps.elicitationTimeout();
    this.samplingTimeout = deps.samplingTimeout();
    this.requestId = requestId;
  }

  @Override
  public void sendResult(R result) {
    if (!resultSent.compareAndSet(false, true)) {
      throw new IllegalStateException("sendResult() has already been called");
    }
    CallToolResult callToolResult = ToolsRegistry.toCallToolResult(result, objectMapper);
    JsonRpcResult jsonRpcResult =
        new JsonRpcResult(objectMapper.valueToTree(callToolResult), requestId);
    stream.publishJson(objectMapper.valueToTree(jsonRpcResult));
    stream.close();
  }

  public boolean isResultSent() {
    return resultSent.get();
  }

  @Override
  public void sendProgress(double progress, double total) {
    if (progressToken == null || progressToken.isNull()) {
      return;
    }
    var params = new ProgressNotificationParams(progressToken, progress, total, null);
    publishNotification(ProgressNotification.METHOD_NAME, objectMapper.valueToTree(params));
  }

  @Override
  public void sendNotification(String method, Object params) {
    JsonNode paramsNode = params != null ? objectMapper.valueToTree(params) : null;
    publishNotification(method, paramsNode);
  }

  @Override
  public void log(LoggingLevel level, String logger, Object data) {
    LoggingLevel threshold = currentLoggingLevel();
    if (level.ordinal() < threshold.ordinal()) {
      return;
    }
    var params =
        new LoggingMessageNotificationParams(level, logger, objectMapper.valueToTree(data), null);
    publishNotification("notifications/message", objectMapper.valueToTree(params));
  }

  @Override
  public void log(LoggingLevel level, String message) {
    log(level, "mcp", message);
  }

  @Override
  public ElicitResult elicit(String message, Consumer<RequestedSchemaBuilder> schema) {
    requireElicitationSupport();
    RequestedSchemaBuilder builder = new RequestedSchemaBuilder();
    schema.accept(builder);
    RequestedSchema requestedSchema = builder.build();
    ObjectNode schemaNode = (ObjectNode) objectMapper.valueToTree(requestedSchema);
    JsonRpcResult result = sendElicitationAndWait(message, requestedSchema);
    return parseRawResponse(result, schemaNode);
  }

  @Override
  public <T> Optional<T> elicit(
      String message, Consumer<RequestedSchemaBuilder> schema, Class<T> resultType) {
    return elicitAndConvert(
        message, schema, content -> objectMapper.treeToValue(content, resultType));
  }

  @Override
  public <T> Optional<T> elicit(
      String message, Consumer<RequestedSchemaBuilder> schema, TypeReference<T> resultType) {
    return elicitAndConvert(
        message, schema, content -> objectMapper.treeToValue(content, resultType));
  }

  private <T> Optional<T> elicitAndConvert(
      String message, Consumer<RequestedSchemaBuilder> schema, Function<JsonNode, T> converter) {
    ElicitResult result = elicit(message, schema);
    if (!result.isAccepted()) {
      return Optional.empty();
    }
    return Optional.of(converter.apply(result.content()));
  }

  @Override
  public CreateMessageResult sample(String prompt, int maxTokens) {
    requireSamplingSupport();

    var requestParams =
        new CreateMessageRequestParams(
            List.of(new SamplingMessage(Role.USER, new TextContent(prompt, null))),
            null,
            null,
            null,
            null,
            maxTokens,
            null,
            null,
            null,
            null,
            null,
            null);

    JsonRpcResult result =
        sendAndWaitForResult(
            "sampling/createMessage",
            objectMapper.valueToTree(requestParams),
            samplingTimeout,
            McpSamplingTimeoutException::new,
            McpSamplingException::new);
    return parseSamplingResult(result);
  }

  private void publishNotification(String method, JsonNode params) {
    JsonRpcNotification notification = JsonRpcNotification.of(method, params);
    stream.publishJson(toJsonTree(notification));
  }

  private ObjectNode toJsonTree(Object value) {
    ObjectNode node = (ObjectNode) objectMapper.valueToTree(value);
    node.properties().removeIf(e -> e.getValue().isNull());
    return node;
  }

  private LoggingLevel currentLoggingLevel() {
    if (sessionId == null || sessionService == null) {
      return LoggingLevel.WARNING;
    }
    return sessionService.find(sessionId).map(McpSession::logLevel).orElse(LoggingLevel.WARNING);
  }

  private McpSession currentSession() {
    if (sessionId == null || sessionService == null) {
      return null;
    }
    return sessionService.find(sessionId).orElse(null);
  }

  private void requireSamplingSupport() {
    McpSession session = currentSession();
    if (session == null || !session.supportsSampling()) {
      throw new McpSamplingNotSupportedException("Client does not support sampling");
    }
  }

  private CreateMessageResult parseSamplingResult(JsonRpcResult result) {
    return objectMapper.treeToValue(result.result(), CreateMessageResult.class);
  }

  private void requireElicitationSupport() {
    McpSession session = currentSession();
    if (session == null || !session.supportsElicitationForm()) {
      throw new McpElicitationNotSupportedException(
          "Client does not support form-based elicitation");
    }
  }

  private JsonRpcResult sendElicitationAndWait(String message, RequestedSchema requestedSchema) {
    ElicitRequestFormParams formParams =
        new ElicitRequestFormParams("form", message, requestedSchema, null, null);
    return sendAndWaitForResult(
        "elicitation/create",
        objectMapper.valueToTree(formParams),
        elicitationTimeout,
        McpElicitationTimeoutException::new,
        McpElicitationException::new);
  }

  private JsonRpcResult sendAndWaitForResult(
      String method,
      JsonNode params,
      Duration timeout,
      Function<String, ? extends RuntimeException> timeoutExceptionFactory,
      Function<String, ? extends RuntimeException> errorExceptionFactory) {
    String jsonRpcId = UUID.randomUUID().toString();
    JsonRpcCall request = JsonRpcCall.of(method, params, objectMapper.valueToTree(jsonRpcId));
    Mailbox<JsonRpcResponse> mailbox = mailboxFactory.create(jsonRpcId, JsonRpcResponse.class);
    try {
      stream.publishJson(toJsonTree(request));
      Optional<JsonRpcResponse> raw = mailbox.poll(timeout);
      if (raw.isEmpty()) {
        throw timeoutExceptionFactory.apply(method + " timed out after " + timeout);
      }
      return switch (raw.get()) {
        case JsonRpcError error ->
            throw errorExceptionFactory.apply("Client returned JSON-RPC error: " + error.error());
        case JsonRpcResult result -> result;
      };
    } finally {
      mailbox.delete();
    }
  }

  private ElicitResult parseRawResponse(JsonRpcResult result, ObjectNode schemaNode) {
    ElicitResult elicitResult = objectMapper.treeToValue(result.result(), ElicitResult.class);
    if (elicitResult.action() != ElicitAction.ACCEPT) {
      return new ElicitResult(elicitResult.action(), null);
    }
    validateContent(elicitResult.content(), schemaNode);
    return elicitResult;
  }

  private void validateContent(JsonNode content, ObjectNode schemaNode) {
    Schema schema = new SchemaLoader(new JsonParser(schemaNode.toString()).parse()).load();
    Validator validator = Validator.forSchema(schema);
    ValidationFailure failure = validator.validate(new JsonParser(content.toString()).parse());
    if (failure != null) {
      throw new McpElicitationException(
          "Elicitation response content failed schema validation: " + failure.getMessage());
    }
  }
}
