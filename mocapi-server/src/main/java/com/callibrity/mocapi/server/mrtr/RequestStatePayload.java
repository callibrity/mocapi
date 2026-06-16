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
package com.callibrity.mocapi.server.mrtr;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The self-contained state folded into an MRTR {@code requestState} token (ADR-0021). The server
 * stores nothing between round trips — this payload, signed and encrypted by {@link
 * RequestStateCodec}, <em>is</em> the state.
 *
 * @param method the JSON-RPC method the state was issued for ({@code tools/call}, {@code
 *     prompts/get}, or {@code resources/read}); a retry arriving on a different method is rejected
 * @param originalParams the original request params (minus {@code _meta}, {@code inputResponses},
 *     and {@code requestState}); used to verify the retry targets the same tool/prompt/resource
 * @param inputResponses the response ledger in call-ordinal order: every elicitation issued so far,
 *     answered or pending (see {@link ResponseLedgerEntry})
 * @param issuedAt epoch milliseconds when the token was minted; tokens older than the configured
 *     TTL are rejected
 * @param principal the authenticated principal the token was issued for, or {@code null} when the
 *     request was unauthenticated; a retry presented by a different principal is rejected (the MRTR
 *     replay-prevention guidance — bind the state to its principal)
 */
public record RequestStatePayload(
    String method,
    JsonNode originalParams,
    List<ResponseLedgerEntry> inputResponses,
    long issuedAt,
    String principal) {}
