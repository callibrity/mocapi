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

import java.util.List;
import java.util.function.Function;

/**
 * The result of one MRTR replay round trip (ADR-0021/ADR-0039): either the handler ran to
 * completion and produced {@code R}, or it hit an unanswered elicitation of type {@code Q} and
 * unwound. Generic so the same shape serves both {@link ReplayExecutor#execute} (the untyped {@code
 * Object}/{@link com.callibrity.mocapi.model.ElicitRequestFormParams} replay core) and {@link
 * com.callibrity.mocapi.server.tools.ToolCallReplayInvoker#invoke} (the typed detached
 * tool-invocation seam) without a second, parallel hierarchy.
 *
 * @param <R> the handler's completed result type
 * @param <Q> the elicitation request type carried by an unwind
 */
public sealed interface ReplayOutcome<R, Q> {

  /**
   * Folds this outcome into a single value, dispatching to the branch matching the actual variant.
   * Gives both type parameters {@code R}/{@code Q} a genuine use on the interface itself, not just
   * on the record implementors.
   *
   * @param onCompleted applied when this outcome is a {@link Completed}
   * @param onInputRequired applied when this outcome is an {@link InputRequired}
   * @param <T> the folded result type
   * @return the value produced by whichever branch matches
   */
  <T> T fold(
      Function<Completed<R, Q>, T> onCompleted, Function<InputRequired<R, Q>, T> onInputRequired);

  /** The handler ran to completion and produced {@code result}. */
  record Completed<R, Q>(R result) implements ReplayOutcome<R, Q> {

    @Override
    public <T> T fold(
        Function<Completed<R, Q>, T> onCompleted,
        Function<InputRequired<R, Q>, T> onInputRequired) {
      return onCompleted.apply(this);
    }
  }

  /**
   * The handler unwound at an unanswered elicitation.
   *
   * @param key the {@code inputRequests} key issued for the pending elicitation
   * @param request the elicitation request the handler built
   * @param ledger the response ledger as of the unwind, including the newly issued slot
   */
  record InputRequired<R, Q>(String key, Q request, List<ResponseLedgerEntry> ledger)
      implements ReplayOutcome<R, Q> {

    public InputRequired {
      ledger = List.copyOf(ledger);
    }

    @Override
    public <T> T fold(
        Function<Completed<R, Q>, T> onCompleted,
        Function<InputRequired<R, Q>, T> onInputRequired) {
      return onInputRequired.apply(this);
    }
  }
}
