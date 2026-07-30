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

import com.callibrity.mocapi.model.ElicitResult;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One slot in the MRTR response ledger (ADR-0021). Each slot corresponds to one {@code
 * ctx.elicit(...)} call ordinal reached during handler execution: the Nth elicit reached maps to
 * the Nth ledger entry. The entry is created (with a {@code null} response) when the elicitation is
 * first issued and filled in when the client's retry answers it.
 *
 * @param key the {@code inputRequests} key the server issued for this elicitation ({@code
 *     "elicit-<ordinal>"}); the client's {@code inputResponses} entry must use the same key
 * @param fingerprint hash of the elicitation request (message + requested schema) captured when the
 *     slot was issued; on replay, the elicit call reaching this ordinal must produce the same
 *     fingerprint or the handler has violated the MRTR idempotency contract
 * @param response the client's answer, or {@code null} while the elicitation is unanswered
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseLedgerEntry(String key, String fingerprint, ElicitResult response) {

  /** Returns true once the client has answered this slot. */
  public boolean isAnswered() {
    return response != null;
  }

  /** Returns a copy of this entry with the given response filled in. */
  public ResponseLedgerEntry answeredWith(ElicitResult result) {
    return new ResponseLedgerEntry(key, fingerprint, result);
  }
}
