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
package com.callibrity.mocapi.examples.tasksredis;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.tasks.McpTask;
import org.springframework.stereotype.Component;

/**
 * Demo tools for the MCP Tasks extension ({@code io.modelcontextprotocol/tasks}). Each tool is an
 * otherwise-ordinary {@code @McpTool} method; {@code @McpTask} is the entire task-enabling surface,
 * so the same handler serves both task-capable and plain synchronous callers (except {@link
 * #mustRunAsTask()}, which opts out of that fallback). See the module README for the conversation
 * this is meant to demonstrate.
 */
@Component
public class TaskDemoTools {

  private static final String PUBLISH_PROP = "publish";

  /**
   * Loops {@code items} times, sleeping briefly and emitting counting progress each iteration — for
   * a task-capable client this shows up as a moving {@code statusMessage} across successive {@code
   * tasks/get} polls; for a plain client it is silent and the call simply blocks until done.
   */
  @McpTool(name = "batch_resize", description = "\"Resizes\" a batch of images, reporting progress")
  @McpTask(ttl = "PT10M", pollInterval = "PT1S")
  public BatchResizeResult batchResize(int items, McpToolContext ctx) throws InterruptedException {
    var progress = ctx.countingProgress((long) items);
    for (int i = 1; i <= items; i++) {
      Thread.sleep(300);
      progress.emit("item " + i);
    }
    return new BatchResizeResult(items, "Resized " + items + " image(s)");
  }

  /**
   * Does a bit of "work", then elicits confirmation before "publishing" — for a task-capable
   * client, that elicitation surfaces as the task reaching {@code input_required} with a pending
   * {@code inputRequests} entry, answered via {@code tasks/update} rather than a wire retry (see
   * the MCP Tasks guide). Per the idempotency contract, the report "compilation" below re-runs on
   * each round trip; only the branch after the elicit call — which differs for accept vs. decline —
   * is side-effecting.
   */
  @McpTool(
      name = "confirmed_report",
      description = "Compiles a report and elicits confirmation before \"publishing\" it")
  @McpTask
  public ReportResult confirmedReport(String subject, McpToolContext ctx) {
    String report = "Report on " + subject + " compiled.";
    ElicitResult answer =
        ctx.elicit(
            "Publish the report about " + subject + "?",
            schema -> schema.bool(PUBLISH_PROP, "Publish?"));
    boolean published = answer.isAccepted() && answer.getBool(PUBLISH_PROP);
    String message =
        published
            ? report + " Published."
            : report + " Left unpublished (" + answer.action() + ").";
    return new ReportResult(subject, published, message);
  }

  /**
   * Registered {@code required = true}: a client that has not declared the {@code tasks} capability
   * is rejected with JSON-RPC {@code -32021} instead of falling back to synchronous execution —
   * demonstrates the "actively wrong to run synchronously" corner of the decision rule.
   */
  @McpTool(
      name = "must_run_as_task",
      description = "Task-only tool: rejects non-capable callers with -32021")
  @McpTask(required = true)
  public MustRunAsTaskResult mustRunAsTask() {
    return new MustRunAsTaskResult("Ran as a task.");
  }

  /** Structured result of {@link #batchResize(int, McpToolContext)}. */
  public record BatchResizeResult(int items, String message) {}

  /** Structured result of {@link #confirmedReport(String, McpToolContext)}. */
  public record ReportResult(String subject, boolean published, String message) {}

  /** Structured result of {@link #mustRunAsTask()}. */
  public record MustRunAsTaskResult(String message) {}
}
