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
package com.callibrity.mocapi.server.compliance;

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.MAPPER;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.MRTR_SECRET;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.buildServer;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.callWithMeta;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.captureError;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.captureResult;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.envelope;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.envelopeWithElicitation;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.mrtrEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.mocapi.server.prompts.GetPromptHandler;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.StructuredResultMapper;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MCP 2026-07-28 § Multi Round-Trip Requests — elicitation by replay (ADR-0021), exercised over the
 * full {@link McpServer} dispatch path: {@code _meta} envelope, JSON-RPC dispatch, MRTR engine,
 * handler re-execution, and the wire shapes of {@code InputRequiredResult}, retry params, and the
 * spec error codes ({@code -32602} invalid state, {@code -32003} missing client capability).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MrtrElicitationComplianceTest {

  private McpServer server;
  private AtomicInteger oneQuestionExecutions;
  private AtomicInteger fickleExecutions;

  @BeforeEach
  void setUp() {
    server = serverWith(mrtrEngine());
  }

  private McpServer serverWith(MrtrElicitationEngine engine) {
    oneQuestionExecutions = new AtomicInteger();
    fickleExecutions = new AtomicInteger();
    var inputSchema = MAPPER.createObjectNode().put("type", "object");

    CallToolHandler oneQuestion =
        new CallToolHandler(
            new Tool("one-question", null, "Asks for an email", inputSchema, null),
            null,
            null,
            arguments -> {
              oneQuestionExecutions.incrementAndGet();
              McpToolContext ctx = McpToolContext.CURRENT.get();
              ElicitResult email = ctx.elicit(new ElicitRequestFormParams("Your email?", null));
              if (!email.isAccepted()) {
                return Map.of("status", "aborted:" + email.action());
              }
              return Map.of("status", "registered:" + email.getString("email"));
            },
            List.of(),
            new StructuredResultMapper(MAPPER));

    CallToolHandler twoQuestions =
        new CallToolHandler(
            new Tool("two-questions", null, "Asks for email and age", inputSchema, null),
            null,
            null,
            arguments -> {
              McpToolContext ctx = McpToolContext.CURRENT.get();
              ElicitResult email = ctx.elicit(new ElicitRequestFormParams("Your email?", null));
              ElicitResult age = ctx.elicit(new ElicitRequestFormParams("Your age?", null));
              return Map.of("summary", email.getString("email") + ":" + age.getString("age"));
            },
            List.of(),
            new StructuredResultMapper(MAPPER));

    CallToolHandler fickle =
        new CallToolHandler(
            new Tool("fickle", null, "Violates the idempotency contract", inputSchema, null),
            null,
            null,
            arguments -> {
              McpToolContext ctx = McpToolContext.CURRENT.get();
              ElicitResult answer =
                  ctx.elicit(
                      new ElicitRequestFormParams(
                          "question-" + fickleExecutions.getAndIncrement(), null));
              return Map.of("answer", answer.content().toString());
            },
            List.of(),
            new StructuredResultMapper(MAPPER));

    var toolsService =
        new McpToolsService(List.of(oneQuestion, twoQuestions, fickle), MAPPER, engine);

    GetPromptHandler greet =
        new GetPromptHandler(
            new Prompt("greet", "Greet", "Greets", null, List.of()),
            null,
            null,
            args ->
                new GetPromptResult(
                    "Greets",
                    List.of(new PromptMessage(Role.USER, new TextContent("hi", null))),
                    ResultTypes.COMPLETE),
            List.of(),
            List.of());
    var promptsService = new McpPromptsService(List.of(greet), engine);

    ReadResourceHandler readme =
        new ReadResourceHandler(
            new Resource("file:///readme", "Readme", "The readme", "text/plain"),
            null,
            null,
            ignored ->
                new ReadResourceResult(
                    List.of(new TextResourceContents("file:///readme", "text/plain", "hello")),
                    0L,
                    CacheScope.PRIVATE,
                    ResultTypes.COMPLETE),
            List.of());
    var resourcesService = new McpResourcesService(List.of(readme), List.of(), engine);

    return buildServer(toolsService, promptsService, resourcesService);
  }

  // --- helpers -----------------------------------------------------------

  private JsonNode callTool(String name, Map<String, Object> extraParams, ObjectNode meta) {
    var transport = mock(McpTransport.class);
    Map<String, Object> params = new HashMap<>();
    params.put("name", name);
    params.put("arguments", Map.of());
    params.putAll(extraParams);
    server.handleCall(callWithMeta("tools/call", params, meta), transport);
    return captureResult(transport).result();
  }

  private JsonNode firstRoundTrip(String tool) {
    return callTool(tool, Map.of(), envelopeWithElicitation());
  }

  private Map<String, Object> acceptAnswer(String key, Map<String, Object> content) {
    return Map.of(key, Map.of("action", "accept", "content", content));
  }

  private Map<String, Object> retryParams(String requestState, Map<String, Object> answers) {
    return Map.of("requestState", requestState, "inputResponses", answers);
  }

  private static String tamper(String token) {
    byte[] bytes = Base64.getUrlDecoder().decode(token);
    bytes[bytes.length - 1] ^= 0x01;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  // --- scenarios ---------------------------------------------------------

  @Nested
  class One_elicitation_two_round_trips {

    @Test
    void first_trip_returns_input_required_with_the_elicitation_and_an_opaque_state() {
      JsonNode result = firstRoundTrip("one-question");

      assertThat(result.path("resultType").asString()).isEqualTo("input_required");
      JsonNode request = result.path("inputRequests").path("elicit-1");
      assertThat(request.path("method").asString()).isEqualTo("elicitation/create");
      assertThat(request.path("params").path("message").asString()).isEqualTo("Your email?");
      assertThat(result.path("requestState").asString()).isNotBlank();
    }

    @Test
    void second_trip_with_the_answer_completes_and_the_handler_ran_twice() {
      JsonNode firstTrip = firstRoundTrip("one-question");
      String state = firstTrip.path("requestState").asString();

      JsonNode result =
          callTool(
              "one-question",
              retryParams(state, acceptAnswer("elicit-1", Map.of("email", "user@example.com"))),
              envelopeWithElicitation());

      assertThat(result.path("resultType").asString()).isEqualTo("complete");
      assertThat(result.path("structuredContent").path("status").asString())
          .isEqualTo("registered:user@example.com");
      assertThat(oneQuestionExecutions).hasValue(2);
    }
  }

  @Nested
  class Two_elicitations_three_round_trips {

    @Test
    void each_unanswered_elicitation_yields_its_own_round_trip() {
      JsonNode firstTrip = firstRoundTrip("two-questions");
      assertThat(
              firstTrip
                  .path("inputRequests")
                  .path("elicit-1")
                  .path("params")
                  .path("message")
                  .asString())
          .isEqualTo("Your email?");

      JsonNode secondTrip =
          callTool(
              "two-questions",
              retryParams(
                  firstTrip.path("requestState").asString(),
                  acceptAnswer("elicit-1", Map.of("email", "user@example.com"))),
              envelopeWithElicitation());
      assertThat(secondTrip.path("resultType").asString()).isEqualTo("input_required");
      assertThat(
              secondTrip
                  .path("inputRequests")
                  .path("elicit-2")
                  .path("params")
                  .path("message")
                  .asString())
          .isEqualTo("Your age?");

      JsonNode thirdTrip =
          callTool(
              "two-questions",
              retryParams(
                  secondTrip.path("requestState").asString(),
                  acceptAnswer("elicit-2", Map.of("age", "42"))),
              envelopeWithElicitation());
      assertThat(thirdTrip.path("resultType").asString()).isEqualTo("complete");
      assertThat(thirdTrip.path("structuredContent").path("summary").asString())
          .isEqualTo("user@example.com:42");
    }
  }

  @Nested
  class Decline_and_cancel {

    @Test
    void declined_elicitation_is_delivered_to_the_handler() {
      JsonNode firstTrip = firstRoundTrip("one-question");

      JsonNode result =
          callTool(
              "one-question",
              retryParams(
                  firstTrip.path("requestState").asString(),
                  Map.of("elicit-1", Map.of("action", "decline"))),
              envelopeWithElicitation());

      assertThat(result.path("structuredContent").path("status").asString())
          .isEqualTo("aborted:DECLINE");
    }

    @Test
    void cancelled_elicitation_is_delivered_to_the_handler() {
      JsonNode firstTrip = firstRoundTrip("one-question");

      JsonNode result =
          callTool(
              "one-question",
              retryParams(
                  firstTrip.path("requestState").asString(),
                  Map.of("elicit-1", Map.of("action", "cancel"))),
              envelopeWithElicitation());

      assertThat(result.path("structuredContent").path("status").asString())
          .isEqualTo("aborted:CANCEL");
    }
  }

  @Nested
  class Invalid_request_state {

    @Test
    void tampered_state_is_rejected_with_invalid_params_and_no_replay() {
      JsonNode firstTrip = firstRoundTrip("one-question");
      int executionsBefore = oneQuestionExecutions.get();
      String tampered = tamper(firstTrip.path("requestState").asString());

      var transport = mock(McpTransport.class);
      Map<String, Object> params = new HashMap<>();
      params.put("name", "one-question");
      params.put("arguments", Map.of());
      params.putAll(
          retryParams(tampered, acceptAnswer("elicit-1", Map.of("email", "user@example.com"))));
      server.handleCall(callWithMeta("tools/call", params, envelopeWithElicitation()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.message()).contains("requestState");
      assertThat(oneQuestionExecutions).hasValue(executionsBefore);
    }

    @Test
    void expired_state_is_rejected_with_invalid_params() {
      Instant start = Instant.parse("2026-06-11T12:00:00Z");
      var mintingServer =
          serverWith(
              new MrtrElicitationEngine(
                  RequestStateCodec.withSecret(
                      MRTR_SECRET,
                      Duration.ofMinutes(5),
                      MAPPER,
                      Clock.fixed(start, ZoneOffset.UTC)),
                  MAPPER));
      var mintingTransport = mock(McpTransport.class);
      Map<String, Object> freshParams = Map.of("name", "one-question", "arguments", Map.of());
      mintingServer.handleCall(
          callWithMeta("tools/call", freshParams, envelopeWithElicitation()), mintingTransport);
      String state = captureResult(mintingTransport).result().path("requestState").asString();

      var lateServer =
          serverWith(
              new MrtrElicitationEngine(
                  RequestStateCodec.withSecret(
                      MRTR_SECRET,
                      Duration.ofMinutes(5),
                      MAPPER,
                      Clock.fixed(start.plus(Duration.ofMinutes(6)), ZoneOffset.UTC)),
                  MAPPER));
      var transport = mock(McpTransport.class);
      Map<String, Object> params = new HashMap<>();
      params.put("name", "one-question");
      params.put("arguments", Map.of());
      params.putAll(
          retryParams(state, acceptAnswer("elicit-1", Map.of("email", "user@example.com"))));
      lateServer.handleCall(
          callWithMeta("tools/call", params, envelopeWithElicitation()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.message()).contains("expired");
    }

    @Test
    void non_elicitation_input_response_at_an_elicitation_key_is_rejected() {
      JsonNode firstTrip = firstRoundTrip("one-question");
      String state = firstTrip.path("requestState").asString();

      // A CreateMessageResult-shaped response (deduced by role/content/model) at a key the server
      // issued an elicitation/create request for.
      Map<String, Object> samplingShapedResponse =
          Map.of(
              "elicit-1",
              Map.of(
                  "role",
                  "assistant",
                  "content",
                  Map.of("type", "text", "text", "hi"),
                  "model",
                  "some-model"));
      var transport = mock(McpTransport.class);
      Map<String, Object> params = new HashMap<>();
      params.put("name", "one-question");
      params.put("arguments", Map.of());
      params.putAll(retryParams(state, samplingShapedResponse));
      server.handleCall(callWithMeta("tools/call", params, envelopeWithElicitation()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.message()).contains("elicit-1");
    }
  }

  @Nested
  class Idempotency_contract {

    @Test
    void handler_asking_a_different_question_on_replay_is_rejected_with_a_diagnostic() {
      JsonNode firstTrip = firstRoundTrip("fickle");
      String state = firstTrip.path("requestState").asString();

      var transport = mock(McpTransport.class);
      Map<String, Object> params = new HashMap<>();
      params.put("name", "fickle");
      params.put("arguments", Map.of());
      params.putAll(retryParams(state, acceptAnswer("elicit-1", Map.of("answer", "yes"))));
      server.handleCall(callWithMeta("tools/call", params, envelopeWithElicitation()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.message()).contains("idempotency contract");
    }
  }

  @Nested
  class Missing_client_capability {

    @Test
    void
        elicit_against_a_client_without_the_capability_is_error_32003_with_required_capabilities() {
      var transport = mock(McpTransport.class);
      Map<String, Object> params = Map.of("name", "one-question", "arguments", Map.of());
      // envelope() declares NO elicitation capability.
      server.handleCall(callWithMeta("tools/call", params, envelope()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(-32003);
      assertThat(
              error.data().path("requiredCapabilities").path("elicitation").path("form").isObject())
          .isTrue();
    }
  }

  @Nested
  class Prompts_and_resources_retry_paths {

    @Test
    void prompts_get_validates_request_state_and_rejects_a_foreign_token() {
      JsonNode firstTrip = firstRoundTrip("one-question");
      String toolToken = firstTrip.path("requestState").asString();

      var transport = mock(McpTransport.class);
      Map<String, Object> params =
          Map.of(
              "name",
              "greet",
              "requestState",
              toolToken,
              "inputResponses",
              acceptAnswer("elicit-1", Map.of("email", "user@example.com")));
      server.handleCall(callWithMeta("prompts/get", params, envelopeWithElicitation()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.message()).contains("prompts/get");
    }

    @Test
    void resources_read_validates_request_state_and_rejects_a_tampered_token() {
      JsonNode firstTrip = firstRoundTrip("one-question");
      String tampered = tamper(firstTrip.path("requestState").asString());

      var transport = mock(McpTransport.class);
      Map<String, Object> params =
          Map.of(
              "uri",
              "file:///readme",
              "requestState",
              tampered,
              "inputResponses",
              acceptAnswer("elicit-1", Map.of("email", "user@example.com")));
      server.handleCall(
          callWithMeta("resources/read", params, envelopeWithElicitation()), transport);

      var error = captureError(transport).error();
      assertThat(error.code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.message()).contains("requestState");
    }

    @Test
    void prompts_get_without_request_state_still_works_normally() {
      var transport = mock(McpTransport.class);
      server.handleCall(
          callWithMeta("prompts/get", Map.of("name", "greet"), envelopeWithElicitation()),
          transport);

      JsonNode result = captureResult(transport).result();
      assertThat(result.path("resultType").asString()).isEqualTo("complete");
      assertThat(result.path("messages")).hasSize(1);
    }
  }
}
