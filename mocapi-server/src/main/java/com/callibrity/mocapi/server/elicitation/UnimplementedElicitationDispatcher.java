/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.elicitation;

import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;

/**
 * Placeholder {@link ElicitationDispatcher} that keeps {@code ctx.elicit(...)} compiling between
 * the Mailbox teardown (ADR-0020) and the MRTR replay engine's arrival (ADR-0021, Phase 4).
 */
public final class UnimplementedElicitationDispatcher implements ElicitationDispatcher {

  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    throw new UnsupportedOperationException("MRTR replay engine arrives in Phase 4");
  }
}
