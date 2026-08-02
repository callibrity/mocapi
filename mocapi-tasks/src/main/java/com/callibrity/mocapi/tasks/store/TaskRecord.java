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
package com.callibrity.mocapi.tasks.store;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.InputRequest;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Durable snapshot of a task's state, as stored by a {@link TaskStore}. May be serialized by
 * external store implementations, hence {@code @JsonInclude(NON_NULL)}.
 *
 * <p>The transition helpers below all guard on {@link TaskStatus#isTerminal()} first: a call
 * against a record already in a terminal status ({@code COMPLETED}, {@code FAILED}, {@code
 * CANCELLED}) returns {@code this} unchanged, since terminal states are final. Otherwise, they
 * rebuild the record with {@code lastUpdatedAt} set to {@code now} and {@code version} bumped by
 * one.
 *
 * @param taskId opaque, server-assigned task identifier
 * @param toolName the {@code @McpTask}-annotated tool being executed
 * @param arguments the original {@code tools/call} arguments
 * @param principal the authenticated caller who created the task
 * @param protocolVersion the MCP protocol version negotiated on the originating request
 * @param clientCapabilities the client's declared capabilities on the originating request
 * @param status current lifecycle status
 * @param statusMessage optional human-readable progress message
 * @param createdAt when the task was created
 * @param lastUpdatedAt when the task was last mutated
 * @param ttl how long after {@code createdAt} the task is considered expired
 * @param pollInterval suggested client poll interval
 * @param ledger the MRTR response ledger (ADR-0021) for this task's execution
 * @param inputRequests outstanding server-initiated requests keyed by {@code inputRequests} key
 * @param result the tool call result, once {@code COMPLETED}
 * @param error the JSON-RPC error detail, once {@code FAILED}
 * @param version optimistic version counter, strictly increasing across transitions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRecord(
    String taskId,
    String toolName,
    JsonNode arguments,
    String principal,
    String protocolVersion,
    ClientCapabilities clientCapabilities,
    TaskStatus status,
    String statusMessage,
    Instant createdAt,
    Instant lastUpdatedAt,
    Duration ttl,
    Duration pollInterval,
    List<ResponseLedgerEntry> ledger,
    Map<String, InputRequest> inputRequests,
    CallToolResult result,
    JsonRpcErrorDetail error,
    long version) {

  /** Returns true if {@code now} is after {@code createdAt + ttl}. */
  public boolean isExpired(Instant now) {
    return now.isAfter(createdAt.plus(ttl));
  }

  /** Transitions to {@code WORKING}. No-op (returns {@code this}) if already terminal. */
  public TaskRecord working(Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(TaskStatus.WORKING, statusMessage, inputRequests, ledger, result, error, now);
  }

  /** Transitions to {@code COMPLETED} with the given result. No-op if already terminal. */
  public TaskRecord completed(CallToolResult result, Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(TaskStatus.COMPLETED, statusMessage, inputRequests, ledger, result, error, now);
  }

  /** Transitions to {@code FAILED} with the given error detail. No-op if already terminal. */
  public TaskRecord failed(JsonRpcErrorDetail error, String statusMessage, Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(TaskStatus.FAILED, statusMessage, inputRequests, ledger, result, error, now);
  }

  /** Transitions to {@code CANCELLED}. No-op if already terminal. */
  public TaskRecord cancelled(Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(TaskStatus.CANCELLED, statusMessage, inputRequests, ledger, result, error, now);
  }

  /**
   * Transitions to {@code INPUT_REQUIRED}, replacing {@code inputRequests} with a single-entry map
   * of {@code key} to {@code request} and updating the ledger. No-op if already terminal.
   */
  public TaskRecord inputRequired(
      String key, InputRequest request, List<ResponseLedgerEntry> ledger, Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(
        TaskStatus.INPUT_REQUIRED, statusMessage, Map.of(key, request), ledger, result, error, now);
  }

  /** Replaces {@code statusMessage}. No-op if already terminal. */
  public TaskRecord withStatusMessage(String message, Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(status, message, inputRequests, ledger, result, error, now);
  }

  /** Replaces {@code ledger}. No-op if already terminal. */
  public TaskRecord withLedger(List<ResponseLedgerEntry> ledger, Instant now) {
    if (status.isTerminal()) {
      return this;
    }
    return advance(status, statusMessage, inputRequests, ledger, result, error, now);
  }

  private TaskRecord advance(
      TaskStatus status,
      String statusMessage,
      Map<String, InputRequest> inputRequests,
      List<ResponseLedgerEntry> ledger,
      CallToolResult result,
      JsonRpcErrorDetail error,
      Instant now) {
    return new TaskRecord(
        taskId,
        toolName,
        arguments,
        principal,
        protocolVersion,
        clientCapabilities,
        status,
        statusMessage,
        createdAt,
        now,
        ttl,
        pollInterval,
        ledger,
        inputRequests,
        result,
        error,
        version + 1);
  }
}
