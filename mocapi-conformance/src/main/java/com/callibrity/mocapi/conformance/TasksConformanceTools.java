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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.BooleanSchema;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.StringSchema;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.tasks.McpTask;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Fixtures required by the {@code @modelcontextprotocol/conformance} suite's {@code
 * io.modelcontextprotocol/tasks} extension scenarios ({@code tasks-lifecycle}, {@code
 * tasks-capability-negotiation}, {@code tasks-wire-fields}, {@code tasks-request-state-removal},
 * {@code tasks-mrtr-input}, {@code tasks-request-headers}, {@code tasks-dispatch-and-envelope},
 * {@code tasks-required-task-error}). Tool names are dictated by the suite (see each scenario's
 * "Required server fixtures" list); they intentionally break the {@code test_*} naming convention
 * the rest of {@link ConformanceTools} follows.
 *
 * <p>Two suite scenarios cannot be produced by these fixtures and are waived in {@code
 * conformance-expected-failures.yaml} instead of implemented here:
 *
 * <ul>
 *   <li>{@code tasks-mrtr-composition} wants a single tool whose FIRST {@code tools/call} returns a
 *       plain, synchronous {@code InputRequiredResult} and whose SECOND call (answering that
 *       elicit) escalates to {@code CreateTaskResult}. {@code @McpTask}'s dispatch happens before
 *       the handler runs at all (every capable call becomes a task from round 1), and ADR-0037 §1
 *       makes that a hard transparency contract: a task-eligible handler body never knows, and
 *       mocapi never lets dispatch logic branch on, which round it is in. Producing the suite's
 *       exact shape needs a new, round-aware dispatch decision — an architectural change, not a
 *       fixture.
 *   <li>{@code tasks-mrtr-input}'s {@code tasks-mrtr-partial-fulfillment} check wants ONE task with
 *       TWO simultaneously pending {@code inputRequests} keys. mocapi's MRTR replay model
 *       (ADR-0021) captures at most one outstanding input-required exception per execution — {@code
 *       ReplayOutcome.InputRequired} and {@code TaskRecord#inputRequired} are both single-key by
 *       construction. {@link #multiInput} below still exercises two elicits, just sequentially (one
 *       key pending at a time), which is a legitimate {@code @McpTask} tool but not what the
 *       simultaneity check verifies.
 * </ul>
 */
@Component
public class TasksConformanceTools {

  private static final String CONFIRM_PROP = "confirm";
  private static final String VALUE_PROP = "value";

  private static CallToolResult text(String message) {
    return new CallToolResult(
        List.of(new TextContent(message, null)), null, null, ResultTypes.COMPLETE);
  }

  /** Scenarios: tasks-lifecycle, tasks-capability-negotiation, tasks-request-headers. */
  @McpTool(name = "greet", description = "Sync-only greeting tool for MCP Tasks conformance")
  public CallToolResult greet(String name) {
    return text("Hello, " + name + "!");
  }

  /**
   * Scenarios: tasks-lifecycle, tasks-capability-negotiation, tasks-wire-fields,
   * tasks-request-state-removal, tasks-request-headers, tasks-dispatch-and-envelope. {@code
   * seconds: 0} exercises the immediate-result path; either a sync {@code ToolResult} or a {@code
   * CreateTaskResult} is spec-valid there, and the {@code @McpTask} decision rule (ADR-0037 §2)
   * always takes the task path for a capable client, which the suite explicitly allows.
   */
  @McpTool(
      name = "slow_compute",
      description = "Task-supporting tool that sleeps `seconds` seconds before completing")
  @McpTask
  public CallToolResult slowCompute(int seconds, String label) throws InterruptedException {
    if (seconds > 0) {
      Thread.sleep(Duration.ofSeconds(seconds));
    }
    return text("slow_compute(" + label + ") finished after " + seconds + "s");
  }

  /**
   * Scenarios: tasks-lifecycle, tasks-dispatch-and-envelope, tasks-required-task-error. Registered
   * {@code required = true} so a non-capable caller is rejected with {@code -32021} (ADR-0037's
   * "Error code: -32021, not the extension draft's -32003" decision) instead of degrading to
   * synchronous execution.
   */
  @McpTool(
      name = "failing_job",
      description = "Task-supporting (required) tool that always reports a tool execution error")
  @McpTask(required = true)
  public CallToolResult failingJob() throws InterruptedException {
    Thread.sleep(Duration.ofSeconds(1));
    return new CallToolResult(
        List.of(new TextContent("failing_job always reports a tool execution error", null)),
        true,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Scenario: tasks-lifecycle (the {@code protocol_error_job} fixture asserting {@code
   * status:"failed"} with an inlined JSON-RPC {@code error}, not a tool-level {@code isError}
   * result). {@code ToolInvocationCore#invokeWithContext} rethrows exactly one exception shape
   * instead of wrapping it into an {@code isError} {@link CallToolResult}: a {@link
   * JsonRpcException} carrying {@link JsonRpcErrorCodes#FORBIDDEN} (the guard-denial code,
   * ADR-0023). {@code TaskExecutionEngine} then catches that propagated exception and writes the
   * task record as {@code failed} with an inlined {@code error{code,message}} — precisely the shape
   * this scenario checks for. The guard-denial code is reused only as the vehicle that survives the
   * rethrow; the failure it produces is a generic internal error, not an authorization denial.
   */
  @McpTool(
      name = "protocol_error_job",
      description = "Task-supporting tool that panics into a protocol-level JSON-RPC error")
  @McpTask
  public CallToolResult protocolErrorJob() {
    throw new JsonRpcException(
        JsonRpcErrorCodes.FORBIDDEN, "protocol_error_job always panics for conformance testing");
  }

  /** Scenarios: tasks-mrtr-input, tasks-dispatch-and-envelope, tasks-request-state-removal. */
  @McpTool(
      name = "confirm_delete",
      description = "Task-supporting tool that elicits confirmation before deleting a file")
  @McpTask
  public CallToolResult confirmDelete(String filename, McpToolContext ctx) {
    ElicitResult answer = ctx.elicit(confirm("Delete " + filename + "?"));
    boolean confirmed = answer.isAccepted() && answer.getBool(CONFIRM_PROP);
    return text(confirmed ? "Deleted " + filename : "Deletion of " + filename + " was cancelled");
  }

  /**
   * Scenario: tasks-mrtr-input ({@code tasks-mrtr-partial-fulfillment} check). Gathers two values
   * across two SEQUENTIAL elicit rounds — see the class javadoc for why this cannot produce the
   * two-simultaneously-pending-keys shape the check verifies.
   */
  @McpTool(
      name = "multi_input",
      description = "Task-supporting tool that gathers two elicited values across two rounds")
  @McpTask
  public CallToolResult multiInput(McpToolContext ctx) {
    ElicitResult first = ctx.elicit(elicitValue("Enter the first value"));
    ElicitResult second = ctx.elicit(elicitValue("Enter the second value"));
    String a = first.isAccepted() ? first.getString(VALUE_PROP) : "n/a";
    String b = second.isAccepted() ? second.getString(VALUE_PROP) : "n/a";
    return text("Gathered: " + a + ", " + b);
  }

  private static ElicitRequestFormParams confirm(String message) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put(CONFIRM_PROP, new BooleanSchema("Confirm", null, null));
    return new ElicitRequestFormParams(
        message, new RequestedSchema(properties, List.of(CONFIRM_PROP), null));
  }

  private static ElicitRequestFormParams elicitValue(String message) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put(VALUE_PROP, new StringSchema("Value", null, null, null, null, null));
    return new ElicitRequestFormParams(
        message, new RequestedSchema(properties, List.of(VALUE_PROP), null));
  }
}
