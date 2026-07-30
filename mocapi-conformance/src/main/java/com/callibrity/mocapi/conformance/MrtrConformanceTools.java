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
package com.callibrity.mocapi.conformance;

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.BooleanSchema;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.StringSchema;
import com.callibrity.mocapi.model.TextContent;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Test tools for the official suite's {@code input-required-result-*} scenarios (SEP-2322, MRTR).
 * Each tool follows the idempotency contract: code before the last {@code elicit(...)} re-executes
 * once per round trip, so everything here is read-only until the final answer arrives.
 *
 * <p>The suite's sampling/roots-based MRTR scenarios ({@code input-required-result-basic-sampling},
 * {@code -basic-list-roots}, {@code -multiple-input-requests}) have no tools here: mocapi does not
 * emit sampling or roots input requests (deprecated features, ADR-0022). They are recorded as
 * expected failures in {@code conformance-expected-failures.yaml}.
 */
@Component
public class MrtrConformanceTools {

  private static final String CONTEXT_KEY = "context";

  private static final BooleanSchema CONFIRM_SCHEMA = new BooleanSchema("Confirm", null, null);

  /** Scenarios: input-required-result-basic-elicitation, input-required-result-result-type. */
  @McpTool(
      name = "test_input_required_result_elicitation",
      description = "Single-elicitation MRTR flow for conformance")
  public CallToolResult basicElicitation(McpToolContext ctx) {
    ElicitResult answer = ctx.elicit(elicitName("What is your name?"));
    String name = answer.isAccepted() ? answer.getString("name") : "stranger";
    return text("Hello, " + name + "!");
  }

  /** Scenario: input-required-result-request-state. */
  @McpTool(
      name = "test_input_required_result_request_state",
      description = "MRTR flow exercising opaque requestState round-tripping")
  public CallToolResult requestState(McpToolContext ctx) {
    ElicitResult answer = ctx.elicit(confirm("Please confirm"));
    boolean ok = answer.isAccepted() && answer.getBool("ok");
    return text("Confirmed: " + ok);
  }

  /** Scenario: input-required-result-multi-round. */
  @McpTool(
      name = "test_input_required_result_multi_round",
      description = "Two sequential elicitations across three round trips")
  public CallToolResult multiRound(McpToolContext ctx) {
    ElicitResult first = ctx.elicit(elicitName("Step 1: What is your name?"));
    if (!first.isAccepted()) {
      return text("Cancelled at step 1");
    }
    ElicitResult second = ctx.elicit(elicitColor("Step 2: What is your favorite color?"));
    if (!second.isAccepted()) {
      return text("Cancelled at step 2");
    }
    return text(first.getString("name") + " likes " + second.getString("color"));
  }

  /** Scenario: input-required-result-tampered-state (rejection happens in the codec). */
  @McpTool(
      name = "test_input_required_result_tampered_state",
      description = "MRTR flow whose requestState integrity the suite tampers with")
  public CallToolResult tamperedState(McpToolContext ctx) {
    ElicitResult answer = ctx.elicit(confirm("Please confirm"));
    return text("Confirmed: " + (answer.isAccepted() && answer.getBool("ok")));
  }

  /** Scenario: input-required-result-capability-check. */
  @McpTool(
      name = "test_input_required_result_capabilities",
      description = "Only emits input requests for methods the client declared")
  public CallToolResult capabilities(McpToolContext ctx) {
    try {
      ElicitResult answer = ctx.elicit(elicitName("What is your name?"));
      String name = answer.isAccepted() ? answer.getString("name") : "stranger";
      return text("Hello, " + name + "!");
    } catch (McpElicitationNotSupportedException _) {
      // Elicitation is the only input-request method mocapi emits (ADR-0022); a client that
      // did not declare it gets a complete result with no input requests.
      return text("No mutually supported input-request methods; completing without input.");
    }
  }

  /** Scenario: input-required-result-non-tool-request (a PROMPT that elicits, ADR-0024). */
  @McpPrompt(
      name = "test_input_required_result_prompt",
      description = "Prompt requiring elicitation input")
  public GetPromptResult inputRequiredPrompt(McpElicitor elicitor) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put(CONTEXT_KEY, new StringSchema("Context", null, null, null, null, null));
    ElicitResult answer =
        elicitor.elicit(
            new ElicitRequestFormParams(
                "What context should the prompt use?",
                new RequestedSchema(properties, List.of(CONTEXT_KEY), null)));
    String context = answer.isAccepted() ? answer.getString(CONTEXT_KEY) : "none";
    return new GetPromptResult(
        "Prompt with elicited context",
        List.of(new PromptMessage(Role.USER, new TextContent("Use context: " + context, null))),
        ResultTypes.COMPLETE);
  }

  private static ElicitRequestFormParams elicitName(String message) {
    return single(message, "name", "Your name");
  }

  private static ElicitRequestFormParams elicitColor(String message) {
    return single(message, "color", "Your favorite color");
  }

  private static ElicitRequestFormParams single(String message, String prop, String title) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put(prop, new StringSchema(title, null, null, null, null, null));
    return new ElicitRequestFormParams(
        message, new RequestedSchema(properties, List.of(prop), null));
  }

  private static ElicitRequestFormParams confirm(String message) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put("ok", CONFIRM_SCHEMA);
    return new ElicitRequestFormParams(
        message, new RequestedSchema(properties, List.of("ok"), null));
  }

  private static CallToolResult text(String message) {
    return new CallToolResult(
        List.of(new TextContent(message, null)), null, null, ResultTypes.COMPLETE);
  }
}
