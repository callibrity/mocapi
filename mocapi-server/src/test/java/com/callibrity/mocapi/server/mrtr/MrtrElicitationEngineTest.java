/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.mrtr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.InputRequiredResult;
import com.callibrity.mocapi.model.InputResponse;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MrtrElicitationEngineTest {

  private static final String SECRET =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private final ObjectMapper mapper = new ObjectMapper();
  private final MrtrElicitationEngine engine =
      new MrtrElicitationEngine(
          RequestStateCodec.withSecret(SECRET, Duration.ofMinutes(5), mapper), mapper);

  private CallToolRequestParams toolParams(
      Map<String, InputResponse> inputResponses, String requestState) {
    return new CallToolRequestParams(
        "onboard",
        mapper.createObjectNode().put("plan", "pro"),
        inputResponses,
        requestState,
        null);
  }

  private ElicitRequestFormParams question(String message) {
    return new ElicitRequestFormParams(message, null);
  }

  private ElicitResult accept(String key, String value) {
    return new ElicitResult(ElicitAction.ACCEPT, mapper.createObjectNode().put(key, value));
  }

  /** A handler that elicits one question and finishes with the answer. */
  private Object oneQuestionHandler() {
    ElicitResult email = engine.elicit(question("Your email?"));
    return "done:" + email.getString("email");
  }

  @Nested
  class Fresh_request {

    @Test
    void handler_without_elicits_returns_its_result_directly() {
      Object result =
          engine.execute(McpMethods.TOOLS_CALL, toolParams(null, null), null, null, () -> "plain");

      assertThat(result).isEqualTo("plain");
    }

    @Test
    void first_unanswered_elicit_yields_input_required_keyed_by_ordinal() {
      Object result =
          engine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(null, null),
              null,
              null,
              MrtrElicitationEngineTest.this::oneQuestionHandler);

      assertThat(result).isInstanceOf(InputRequiredResult.class);
      InputRequiredResult required = (InputRequiredResult) result;
      assertThat(required.resultType()).isEqualTo(ResultTypes.INPUT_REQUIRED);
      assertThat(required.inputRequests()).containsOnlyKeys("elicit-1");
      ElicitRequest request = (ElicitRequest) required.inputRequests().get("elicit-1");
      assertThat(((ElicitRequestFormParams) request.params()).message()).isEqualTo("Your email?");
      assertThat(required.requestState()).isNotBlank();
    }
  }

  @Nested
  class Principal_binding {

    private final AtomicReference<String> principal = new AtomicReference<>();
    private final MrtrElicitationEngine boundEngine =
        new MrtrElicitationEngine(
            RequestStateCodec.withSecret(SECRET, Duration.ofMinutes(5), mapper),
            mapper,
            principal::get);

    private final Supplier<Object> emailHandler =
        () -> "done:" + boundEngine.elicit(question("Your email?")).getString("email");

    private InputRequiredResult firstTrip() {
      return (InputRequiredResult)
          boundEngine.execute(
              McpMethods.TOOLS_CALL, toolParams(null, null), null, null, emailHandler);
    }

    @Test
    void retry_presented_by_a_different_principal_is_rejected() {
      principal.set("alice");
      var first = firstTrip();

      principal.set("bob");
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "user@example.com"));
      String requestState = first.requestState();
      var params = toolParams(answers, requestState);

      assertThatThrownBy(
              () ->
                  boundEngine.execute(
                      McpMethods.TOOLS_CALL, params, answers, requestState, emailHandler))
          .isInstanceOf(JsonRpcException.class)
          .hasMessageContaining("principal");
    }

    @Test
    void retry_by_the_same_principal_succeeds() {
      principal.set("alice");
      var first = firstTrip();

      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "user@example.com"));
      Object result =
          boundEngine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(answers, first.requestState()),
              answers,
              first.requestState(),
              emailHandler);

      assertThat(result).isEqualTo("done:user@example.com");
    }
  }

  @Nested
  class Retry_round_trips {

    @Test
    void answered_elicit_returns_the_result_and_the_handler_completes() {
      AtomicInteger executions = new AtomicInteger();
      var handler =
          (Supplier<Object>)
              () -> {
                executions.incrementAndGet();
                return oneQuestionHandler();
              };

      var firstTrip =
          (InputRequiredResult)
              engine.execute(McpMethods.TOOLS_CALL, toolParams(null, null), null, null, handler);

      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "user@example.com"));
      Object secondTrip =
          engine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(answers, firstTrip.requestState()),
              answers,
              firstTrip.requestState(),
              handler);

      assertThat(secondTrip).isEqualTo("done:user@example.com");
      assertThat(executions).hasValue(2);
    }

    @Test
    void second_elicit_yields_a_new_input_required_whose_state_folds_in_the_first_answer() {
      var handler =
          (Supplier<Object>)
              () -> {
                ElicitResult email = engine.elicit(question("Your email?"));
                ElicitResult age = engine.elicit(question("Your age?"));
                return email.getString("email") + ":" + age.getString("age");
              };

      var firstTrip =
          (InputRequiredResult)
              engine.execute(McpMethods.TOOLS_CALL, toolParams(null, null), null, null, handler);
      Map<String, InputResponse> firstAnswer =
          Map.of("elicit-1", accept("email", "user@example.com"));
      var secondTrip =
          (InputRequiredResult)
              engine.execute(
                  McpMethods.TOOLS_CALL,
                  toolParams(firstAnswer, firstTrip.requestState()),
                  firstAnswer,
                  firstTrip.requestState(),
                  handler);

      assertThat(secondTrip.inputRequests()).containsOnlyKeys("elicit-2");

      Map<String, InputResponse> secondAnswer = Map.of("elicit-2", accept("age", "42"));
      Object thirdTrip =
          engine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(secondAnswer, secondTrip.requestState()),
              secondAnswer,
              secondTrip.requestState(),
              handler);

      assertThat(thirdTrip).isEqualTo("user@example.com:42");
    }

    @Test
    void decline_is_returned_to_the_handler_as_a_normal_answer() {
      var handler =
          (Supplier<Object>)
              () -> {
                ElicitResult answer = engine.elicit(question("Confirm?"));
                return answer.isAccepted() ? "accepted" : "not-accepted:" + answer.action();
              };

      var firstTrip =
          (InputRequiredResult)
              engine.execute(McpMethods.TOOLS_CALL, toolParams(null, null), null, null, handler);
      Map<String, InputResponse> declined =
          Map.of("elicit-1", new ElicitResult(ElicitAction.DECLINE, null));
      Object secondTrip =
          engine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(declined, firstTrip.requestState()),
              declined,
              firstTrip.requestState(),
              handler);

      assertThat(secondTrip).isEqualTo("not-accepted:DECLINE");
    }

    @Test
    void cancel_is_returned_to_the_handler_as_a_normal_answer() {
      var handler =
          (Supplier<Object>)
              () -> {
                ElicitResult answer = engine.elicit(question("Confirm?"));
                return answer.action().name();
              };

      var firstTrip =
          (InputRequiredResult)
              engine.execute(McpMethods.TOOLS_CALL, toolParams(null, null), null, null, handler);
      Map<String, InputResponse> cancelled =
          Map.of("elicit-1", new ElicitResult(ElicitAction.CANCEL, null));
      Object secondTrip =
          engine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(cancelled, firstTrip.requestState()),
              cancelled,
              firstTrip.requestState(),
              handler);

      assertThat(secondTrip).isEqualTo("CANCEL");
    }

    @Test
    void retry_that_does_not_answer_the_pending_question_reissues_the_same_ordinal() {
      var firstTrip =
          (InputRequiredResult)
              engine.execute(
                  McpMethods.TOOLS_CALL,
                  toolParams(null, null),
                  null,
                  null,
                  MrtrElicitationEngineTest.this::oneQuestionHandler);

      Object secondTrip =
          engine.execute(
              McpMethods.TOOLS_CALL,
              toolParams(null, firstTrip.requestState()),
              null,
              firstTrip.requestState(),
              MrtrElicitationEngineTest.this::oneQuestionHandler);

      assertThat(secondTrip).isInstanceOf(InputRequiredResult.class);
      assertThat(((InputRequiredResult) secondTrip).inputRequests()).containsOnlyKeys("elicit-1");
    }
  }

  @Nested
  class Invalid_retries {

    private String mintedToken() {
      var firstTrip =
          (InputRequiredResult)
              engine.execute(
                  McpMethods.TOOLS_CALL,
                  toolParams(null, null),
                  null,
                  null,
                  MrtrElicitationEngineTest.this::oneQuestionHandler);
      return firstTrip.requestState();
    }

    @Test
    void tampered_request_state_is_rejected_with_invalid_params() {
      String token = mintedToken();
      byte[] bytes = Base64.getUrlDecoder().decode(token);
      bytes[bytes.length - 1] ^= 0x01;
      String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      var params = toolParams(answers, tampered);

      assertThatThrownBy(
              () ->
                  engine.execute(
                      McpMethods.TOOLS_CALL, params, answers, tampered, () -> "unreachable"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("requestState");
    }

    @Test
    void expired_request_state_is_rejected_with_invalid_params() {
      Instant start = Instant.parse("2026-06-11T12:00:00Z");
      var minting =
          new MrtrElicitationEngine(
              RequestStateCodec.withSecret(
                  SECRET, Duration.ofMinutes(5), mapper, Clock.fixed(start, ZoneOffset.UTC)),
              mapper);
      var late =
          new MrtrElicitationEngine(
              RequestStateCodec.withSecret(
                  SECRET,
                  Duration.ofMinutes(5),
                  mapper,
                  Clock.fixed(start.plus(Duration.ofMinutes(6)), ZoneOffset.UTC)),
              mapper);
      String token = mintedTokenFrom(minting);
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      var params = toolParams(answers, token);

      assertThatThrownBy(
              () -> late.execute(McpMethods.TOOLS_CALL, params, answers, token, () -> "x"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("expired");
    }

    private String mintedTokenFrom(MrtrElicitationEngine source) {
      var trip =
          (InputRequiredResult)
              source.execute(
                  McpMethods.TOOLS_CALL,
                  toolParams(null, null),
                  null,
                  null,
                  () -> source.elicit(question("Your email?")));
      return trip.requestState();
    }

    @Test
    void token_issued_for_tools_call_is_rejected_on_prompts_get() {
      String token = mintedToken();
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      var promptParams = new GetPromptRequestParams("onboard", null, answers, token, null);

      assertThatThrownBy(
              () -> engine.execute(McpMethods.PROMPTS_GET, promptParams, answers, token, () -> "x"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("tools/call")
          .hasMessageContaining("prompts/get");
    }

    @Test
    void token_issued_for_tools_call_is_rejected_on_resources_read() {
      String token = mintedToken();
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      var resourceParams = new ResourceRequestParams("file:///x", answers, token, null);

      assertThatThrownBy(
              () ->
                  engine.execute(
                      McpMethods.RESOURCES_READ, resourceParams, answers, token, () -> "x"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }

    @Test
    void token_issued_for_a_different_tool_name_is_rejected() {
      String token = mintedToken();
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      var otherTool =
          new CallToolRequestParams("other-tool", mapper.createObjectNode(), answers, token, null);

      assertThatThrownBy(
              () -> engine.execute(McpMethods.TOOLS_CALL, otherTool, answers, token, () -> "x"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("different target");
    }

    @Test
    void input_responses_without_request_state_are_rejected() {
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      var params = toolParams(answers, null);

      assertThatThrownBy(
              () -> engine.execute(McpMethods.TOOLS_CALL, params, answers, null, () -> "x"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("requestState");
    }

    @Test
    void unknown_input_responses_key_is_rejected() {
      String token = mintedToken();
      Map<String, InputResponse> answers = Map.of("elicit-99", accept("email", "a@b.c"));
      var params = toolParams(answers, token);

      assertThatThrownBy(
              () -> engine.execute(McpMethods.TOOLS_CALL, params, answers, token, () -> "x"))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("elicit-99");
    }

    @Test
    void re_answering_an_already_answered_key_is_rejected() {
      var handler =
          (Supplier<Object>)
              () -> {
                engine.elicit(question("Your email?"));
                engine.elicit(question("Your age?"));
                return "done";
              };
      var firstTrip =
          (InputRequiredResult)
              engine.execute(McpMethods.TOOLS_CALL, toolParams(null, null), null, null, handler);
      Map<String, InputResponse> firstAnswer =
          Map.of("elicit-1", accept("email", "user@example.com"));
      var secondTrip =
          (InputRequiredResult)
              engine.execute(
                  McpMethods.TOOLS_CALL,
                  toolParams(firstAnswer, firstTrip.requestState()),
                  firstAnswer,
                  firstTrip.requestState(),
                  handler);
      Map<String, InputResponse> replayedAnswer =
          Map.of("elicit-1", accept("email", "evil@example.com"));
      String token = secondTrip.requestState();
      var params = toolParams(replayedAnswer, token);

      assertThatThrownBy(
              () -> engine.execute(McpMethods.TOOLS_CALL, params, replayedAnswer, token, handler))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("already answered");
    }
  }

  @Nested
  class Idempotency_contract {

    @Test
    void replay_asking_a_different_question_at_an_answered_ordinal_is_rejected_with_diagnostic() {
      AtomicInteger executions = new AtomicInteger();
      var nonDeterministic =
          (Supplier<Object>)
              () -> {
                ElicitResult answer =
                    engine.elicit(question("question-" + executions.getAndIncrement()));
                return answer.getString("email");
              };

      var firstTrip =
          (InputRequiredResult)
              engine.execute(
                  McpMethods.TOOLS_CALL, toolParams(null, null), null, null, nonDeterministic);
      Map<String, InputResponse> answers = Map.of("elicit-1", accept("email", "a@b.c"));
      String token = firstTrip.requestState();
      var params = toolParams(answers, token);

      assertThatThrownBy(
              () -> engine.execute(McpMethods.TOOLS_CALL, params, answers, token, nonDeterministic))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("idempotency contract")
          .hasMessageContaining("elicit-1");
    }
  }

  @Test
  void elicit_outside_an_mrtr_dispatch_throws_illegal_state() {
    var params = question("Anyone there?");
    assertThatThrownBy(() -> engine.elicit(params))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside an MRTR dispatch");
  }
}
