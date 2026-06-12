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
 * Internal seam between {@code ctx.elicit(...)} and the mechanism that obtains the client's answer.
 * MCP 2026-07-28 removed server-initiated requests, so the old rendezvous channel (ADR-0008) is
 * gone; the MRTR replay engine (ADR-0021, Phase 4) is this seam's production implementation. The
 * capability pre-check (does the client support form elicitation?) happens before this seam is
 * consulted.
 */
public interface ElicitationDispatcher {

  /**
   * Obtains the client's response to a form-mode elicitation request.
   *
   * @param params the elicitation request parameters
   * @return the client's elicitation result
   */
  ElicitResult elicit(ElicitRequestFormParams params);
}
