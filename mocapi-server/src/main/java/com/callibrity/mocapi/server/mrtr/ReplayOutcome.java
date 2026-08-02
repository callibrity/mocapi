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
import java.util.List;

/**
 * The result of one {@link ReplayExecutor#execute} round trip (ADR-0021): either the handler ran to
 * completion, or it hit an unanswered elicitation and unwound.
 */
public sealed interface ReplayOutcome {

  /** The handler completed and produced {@code result}. */
  record Completed(Object result) implements ReplayOutcome {}

  /**
   * The handler unwound at an unanswered elicitation.
   *
   * @param key the {@code inputRequests} key issued for the pending elicitation
   * @param params the elicitation request the handler built
   * @param entries the response ledger as of the unwind, including the newly issued slot
   */
  record InputRequired(
      String key, ElicitRequestFormParams params, List<ResponseLedgerEntry> entries)
      implements ReplayOutcome {

    public InputRequired {
      entries = List.copyOf(entries);
    }
  }
}
