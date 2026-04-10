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
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.LoggingMessageNotificationParams;
import com.callibrity.mocapi.model.ProgressNotification;
import com.callibrity.mocapi.model.ProgressNotificationParams;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.SamplingMessage;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStream;
import com.callibrity.mocapi.stream.elicitation.BeanElicitationResult;
import com.callibrity.mocapi.stream.elicitation.ElicitationAction;
import com.callibrity.mocapi.stream.elicitation.ElicitationResult;
import com.callibrity.mocapi.stream.elicitation.ElicitationSchema;
import com.callibrity.mocapi.stream.elicitation.ElicitationSchemaBuilder;
import com.callibrity.mocapi.stream.elicitation.ElicitationSchemaValidator;
import com.callibrity.mocapi.stream.elicitation.McpElicitationException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationTimeoutException;
import com.callibrity.mocapi.tools.ToolsRegistry;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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

  private final McpSessionStream stream;
  private final ObjectMapper objectMapper;
  private final ValueNode progressToken;
  private final MailboxFactory mailboxFactory;
  private final SchemaGenerator schemaGenerator;
  private final McpSessionService sessionService;
  private final String sessionId;
  private final Duration elicitationTimeout;
  private final JsonNode requestId;
  private final AtomicBoolean resultSent = new AtomicBoolean(false);

  public DefaultMcpStreamContext(
      McpSessionStream stream,
      ObjectMapper objectMapper,
      ValueNode progressToken,
      MailboxFactory mailboxFactory,
      SchemaGenerator schemaGenerator,
      McpSessionService sessionService,
      String sessionId,
      Duration elicitationTimeout,
      JsonNode requestId) {
    this.stream = stream;
    this.objectMapper = objectMapper;
    this.progressToken = progressToken;
    this.mailboxFactory = mailboxFactory;
    this.schemaGenerator = schemaGenerator;
    this.sessionService = sessionService;
    this.sessionId = sessionId;
    this.elicitationTimeout = elicitationTimeout;
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
    publishNotification(ProgressNotification.METHOD, objectMapper.valueToTree(params));
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
  public <T> BeanElicitationResult<T> elicitForm(String message, Class<T> type) {
    requireElicitationSupport();
    ObjectNode schemaNode = generateSchema(type);
    return doElicit(message, schemaNode, type);
  }

  @Override
  public <T> BeanElicitationResult<T> elicitForm(String message, TypeReference<T> type) {
    requireElicitationSupport();
    Class<?> rawType = objectMapper.constructType(type).getRawClass();
    ObjectNode schemaNode = generateSchema(rawType);
    return doElicit(message, schemaNode, type);
  }

  @Override
  public ElicitationResult elicit(String message, Consumer<ElicitationSchemaBuilder> schema) {
    requireElicitationSupport();
    ElicitationSchemaBuilder builder = ElicitationSchema.builder();
    schema.accept(builder);
    ObjectNode schemaNode = builder.build().toObjectNode(objectMapper);
    JsonNode rawResponse = sendElicitationAndWait(message, schemaNode);
    return parseRawResponse(rawResponse, schemaNode);
  }

  @Override
  public SamplingResult sample(String prompt, int maxTokens) {
    requireSamplingSupport();

    String jsonRpcId = UUID.randomUUID().toString();

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

    JsonRpcCall request =
        JsonRpcCall.of(
            "sampling/createMessage",
            objectMapper.valueToTree(requestParams),
            objectMapper.valueToTree(jsonRpcId));

    Mailbox<JsonNode> mailbox = mailboxFactory.create("elicit:" + jsonRpcId, JsonNode.class);
    try {
      stream.publishJson(toJsonTree(request));

      Optional<JsonNode> raw = mailbox.poll(elicitationTimeout);
      if (raw.isEmpty()) {
        throw new McpSamplingTimeoutException("Sampling timed out after " + elicitationTimeout);
      }
      return parseSamplingResponse(raw.get());
    } finally {
      mailbox.delete();
    }
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

  private SamplingResult parseSamplingResponse(JsonNode rawResponse) {
    JsonNode errorNode = rawResponse.get("error");
    if (errorNode != null) {
      throw new McpSamplingException("Client returned JSON-RPC error: " + errorNode);
    }

    JsonNode resultNode = rawResponse.get("result");
    if (resultNode == null) {
      resultNode = rawResponse;
    }

    CreateMessageResult result = objectMapper.treeToValue(resultNode, CreateMessageResult.class);
    String role = result.role() != null ? result.role().toJson() : null;
    JsonNode content = result.content() != null ? objectMapper.valueToTree(result.content()) : null;
    return new SamplingResult(role, content, result.model(), result.stopReason());
  }

  private void requireElicitationSupport() {
    McpSession session = currentSession();
    if (session == null || !session.supportsElicitationForm()) {
      throw new McpElicitationNotSupportedException(
          "Client does not support form-based elicitation");
    }
  }

  private ObjectNode generateSchema(Class<?> type) {
    ObjectNode schemaNode = schemaGenerator.generateSchema(type);
    schemaNode.remove("$schema");
    if (!"object".equals(schemaNode.path("type").asString())) {
      throw new McpElicitationException(
          "Elicitation schema must be an object type, but got: " + schemaNode.path("type"));
    }
    ElicitationSchemaValidator.validate(schemaNode);
    return schemaNode;
  }

  private <T> BeanElicitationResult<T> doElicit(
      String message, ObjectNode schemaNode, Class<T> type) {
    JsonNode rawResponse = sendElicitationAndWait(message, schemaNode);
    return parseResponse(rawResponse, schemaNode, type);
  }

  private <T> BeanElicitationResult<T> doElicit(
      String message, ObjectNode schemaNode, TypeReference<T> type) {
    JsonNode rawResponse = sendElicitationAndWait(message, schemaNode);
    return parseResponse(rawResponse, schemaNode, type);
  }

  private JsonNode sendElicitationAndWait(String message, ObjectNode schemaNode) {
    String jsonRpcId = UUID.randomUUID().toString();

    ObjectNode params = objectMapper.createObjectNode();
    params.put("mode", "form");
    params.put("message", message);
    params.set("requestedSchema", schemaNode);

    JsonRpcCall request =
        JsonRpcCall.of("elicitation/create", params, objectMapper.valueToTree(jsonRpcId));

    Mailbox<JsonNode> mailbox = mailboxFactory.create("elicit:" + jsonRpcId, JsonNode.class);
    try {
      stream.publishJson(toJsonTree(request));

      Optional<JsonNode> raw = mailbox.poll(elicitationTimeout);
      if (raw.isEmpty()) {
        throw new McpElicitationTimeoutException(
            "Elicitation timed out after " + elicitationTimeout);
      }
      return raw.get();
    } finally {
      mailbox.delete();
    }
  }

  private <T> BeanElicitationResult<T> parseResponse(
      JsonNode rawResponse, ObjectNode schemaNode, Class<T> type) {
    ElicitationAction action = extractAction(rawResponse);
    if (action != ElicitationAction.ACCEPT) {
      return new BeanElicitationResult<>(action, null);
    }
    JsonNode content = extractContent(rawResponse);
    validateContent(content, schemaNode);
    T typed = objectMapper.treeToValue(content, type);
    return new BeanElicitationResult<>(action, typed);
  }

  private <T> BeanElicitationResult<T> parseResponse(
      JsonNode rawResponse, ObjectNode schemaNode, TypeReference<T> type) {
    ElicitationAction action = extractAction(rawResponse);
    if (action != ElicitationAction.ACCEPT) {
      return new BeanElicitationResult<>(action, null);
    }
    JsonNode content = extractContent(rawResponse);
    validateContent(content, schemaNode);
    T typed = objectMapper.treeToValue(content, type);
    return new BeanElicitationResult<>(action, typed);
  }

  private ElicitationResult parseRawResponse(JsonNode rawResponse, ObjectNode schemaNode) {
    ElicitationAction action = extractAction(rawResponse);
    if (action != ElicitationAction.ACCEPT) {
      return new ElicitationResult(action, null);
    }
    JsonNode content = extractContent(rawResponse);
    validateContent(content, schemaNode);
    return new ElicitationResult(action, content);
  }

  private ElicitationAction extractAction(JsonNode rawResponse) {
    JsonNode errorNode = rawResponse.get("error");
    if (errorNode != null) {
      throw new McpElicitationException("Client returned JSON-RPC error: " + errorNode);
    }

    JsonNode resultNode = rawResponse.get("result");
    if (resultNode == null) {
      resultNode = rawResponse;
    }

    JsonNode actionNode = resultNode.get("action");
    if (actionNode == null) {
      throw new McpElicitationException("Elicitation response missing 'action' field");
    }
    return ElicitationAction.fromValue(actionNode.asString());
  }

  private JsonNode extractContent(JsonNode rawResponse) {
    JsonNode resultNode = rawResponse.get("result");
    if (resultNode == null) {
      resultNode = rawResponse;
    }
    JsonNode content = resultNode.get("content");
    if (content == null || content.isNull()) {
      throw new McpElicitationException("Accepted elicitation response missing 'content' field");
    }
    return content;
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
