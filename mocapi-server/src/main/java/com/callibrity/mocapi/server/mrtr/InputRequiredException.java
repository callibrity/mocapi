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

/**
 * Internal control-flow exception thrown by {@code ctx.elicit(...)} when the elicitation at the
 * current call ordinal has no answer in the response ledger (ADR-0021). {@link
 * MrtrElicitationEngine#execute} catches it and converts it into the {@code InputRequiredResult}
 * round-trip response; it is never user-visible and never crosses the dispatch boundary. Stack
 * traces are suppressed — this is unwinding, not an error.
 *
 * <p>Code that wraps handler exceptions generically (e.g. tool-error mapping) must rethrow this
 * type so the engine can see it.
 */
public final class InputRequiredException extends RuntimeException {

  /** Not serializable; the exception never outlives the dispatch stack it unwinds. */
  private final transient String key;

  /** Not serializable; the exception never outlives the dispatch stack it unwinds. */
  private final transient ElicitRequestFormParams params;

  InputRequiredException(String key, ElicitRequestFormParams params) {
    super(
        "Elicitation \""
            + key
            + "\" awaits a client response (internal MRTR control-flow exception)",
        null,
        false,
        false);
    this.key = key;
    this.params = params;
  }

  /** The {@code inputRequests} key issued for the pending elicitation. */
  public String key() {
    return key;
  }

  /** The elicitation request the handler built. */
  public ElicitRequestFormParams params() {
    return params;
  }
}
