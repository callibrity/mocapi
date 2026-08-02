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

import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.server.elicitation.ElicitationDispatcher;
import com.callibrity.mocapi.server.util.Hashes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

/**
 * The MRTR replay core (ADR-0021): call-ordinal cursor, per-slot fingerprints, and the conversion
 * of an unanswered elicitation into a {@link ReplayOutcome.InputRequired}. Extracted from {@code
 * MrtrElicitationEngine} so a future task-store carrier can reuse the replay mechanics without the
 * wire-token machinery ({@code RequestStateCodec}, principal/target verification) that only the
 * request/retry (non-tasks) transport needs.
 *
 * <p>{@link #execute} does not catch {@link ElicitationLedgerMismatchException}; that exception
 * keeps propagating so each carrier can translate it on its own terms (the wire engine maps it to
 * {@code -32602}).
 */
public final class ReplayExecutor implements ElicitationDispatcher {

  private static final ScopedValue<ReplayExecution> EXECUTION = ScopedValue.newInstance();

  private static final String KEY_PREFIX = "elicit-";

  private final ObjectMapper objectMapper;

  public ReplayExecutor(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  /**
   * The {@code ctx.elicit(...)} seam: consults the current execution's response ledger by call
   * ordinal. Answered → returns the recorded {@link ElicitResult}; unanswered → raises {@link
   * InputRequiredException} carrying the built request.
   *
   * @throws IllegalStateException if called outside an MRTR-capable dispatch (only {@code
   *     tools/call}, {@code prompts/get}, and {@code resources/read} handler executions on the
   *     dispatch thread can elicit; detached async threads cannot)
   */
  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    if (!EXECUTION.isBound()) {
      throw new IllegalStateException(
          "ctx.elicit(...) called outside an MRTR dispatch. Elicitation is only available while "
              + "the handler executes on the dispatch thread of tools/call, prompts/get, or "
              + "resources/read — not from detached async threads.");
    }
    return EXECUTION.get().elicit(params, fingerprintOf(params));
  }

  /**
   * Runs the handler with {@code ledger} bound as the current execution's response ledger, and
   * converts a pending elicitation into a {@link ReplayOutcome.InputRequired}.
   *
   * @param ledger the response ledger to replay against (empty on a fresh request)
   * @param invocation invokes the handler from the top
   * @return {@link ReplayOutcome.Completed} with the handler's result, or {@link
   *     ReplayOutcome.InputRequired} if it elicited an unanswered question
   */
  public ReplayOutcome execute(List<ResponseLedgerEntry> ledger, Supplier<Object> invocation) {
    ReplayExecution execution = new ReplayExecution(ledger);
    try {
      Object result = ScopedValue.where(EXECUTION, execution).call(invocation::get);
      return new ReplayOutcome.Completed(result);
    } catch (InputRequiredException signal) {
      return new ReplayOutcome.InputRequired(signal.key(), signal.params(), execution.entries());
    }
  }

  private String fingerprintOf(ElicitRequestFormParams params) {
    return Hashes.sha256Of(objectMapper.valueToTree(params).toString());
  }

  /** Per-dispatch replay state: the response ledger plus the elicit-call cursor. */
  private static final class ReplayExecution {

    private final List<ResponseLedgerEntry> entries;
    private int cursor;

    ReplayExecution(List<ResponseLedgerEntry> entries) {
      this.entries = new ArrayList<>(entries);
    }

    ElicitResult elicit(ElicitRequestFormParams params, String fingerprint) {
      cursor++;
      if (cursor <= entries.size()) {
        ResponseLedgerEntry entry = entries.get(cursor - 1);
        if (!entry.fingerprint().equals(fingerprint)) {
          throw new ElicitationLedgerMismatchException(
              String.format(
                  "MRTR replay mismatch at elicitation #%d (key \"%s\"): the handler asked a "
                      + "different question than the one this ledger position answers. Handlers "
                      + "must honor the MRTR idempotency contract: code before the last elicit() "
                      + "re-executes once per round trip, and the Nth elicit() reached during a "
                      + "replay must issue the same message and schema as the original execution.",
                  cursor, entry.key()));
        }
        if (entry.isAnswered()) {
          return entry.response();
        }
        throw new InputRequiredException(entry.key(), params);
      }
      String key = KEY_PREFIX + cursor;
      entries.add(new ResponseLedgerEntry(key, fingerprint, null));
      throw new InputRequiredException(key, params);
    }

    List<ResponseLedgerEntry> entries() {
      return List.copyOf(entries);
    }
  }
}
