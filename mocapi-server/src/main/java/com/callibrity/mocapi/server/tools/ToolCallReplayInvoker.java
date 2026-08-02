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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.mrtr.ReplayOutcome;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Detached (off-dispatch-thread) re-invocation of a registered tool by name, driven by a
 * caller-supplied response ledger rather than the {@code tools/call} request/retry envelope. This
 * is the seam a future task-store carrier (e.g. {@code mocapi-tasks}) uses to run a tool on a
 * background thread and resume it against a persisted ledger, instead of the synchronous {@link
 * McpToolsService#callTool} dispatch path.
 *
 * <p>Unlike {@link McpToolsService#callTool}, this seam has no wire envelope: no {@code
 * requestState} encode/decode, no principal/target verification. Callers own the ledger's identity
 * and lifecycle; this method only replays it against the handler.
 *
 * <p>This is an API other modules call (not an SPI they implement) — the production implementation
 * is {@link ToolInvocationCore} (ADR-0039).
 */
public interface ToolCallReplayInvoker {

  /**
   * Invokes a registered tool by name against the given response ledger, off the normal {@code
   * tools/call} dispatch path.
   *
   * @param toolName the registered tool name
   * @param arguments the tool's call arguments
   * @param ledger the response ledger to replay against (empty on a fresh invocation)
   * @param progress the progress source the tool's context should emit through
   * @param exchange the client view (capabilities, protocol version) the tool's context exposes
   * @return {@link ReplayOutcome.Completed} with the handler's result, or {@link
   *     ReplayOutcome.InputRequired} if it elicited an unanswered question
   * @throws JsonRpcException if no tool is registered under {@code toolName}
   */
  ReplayOutcome<CallToolResult, ElicitRequest> invoke(
      String toolName,
      JsonNode arguments,
      List<ResponseLedgerEntry> ledger,
      McpProgressSource progress,
      McpExchange exchange);
}
