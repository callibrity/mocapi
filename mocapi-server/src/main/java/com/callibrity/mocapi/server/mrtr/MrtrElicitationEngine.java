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

import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.InputRequiredResult;
import com.callibrity.mocapi.model.InputResponse;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.server.elicitation.ElicitationDispatcher;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The MRTR replay engine (ADR-0021) — the production {@link ElicitationDispatcher}. Elicitation in
 * MCP 2026-07-28 is a request/retry protocol: when a handler needs input, the server returns an
 * {@code InputRequiredResult}; the client gathers answers and retries the original call with {@code
 * inputResponses} plus the opaque {@code requestState}; the handler re-executes from the top with
 * the accumulated answers available.
 *
 * <p><strong>The seam.</strong> {@link #execute} wraps handler invocation inside the three
 * {@code @JsonRpcMethod} service methods for {@code tools/call}, {@code prompts/get}, and {@code
 * resources/read} — exactly the three RPC methods whose params extend the spec's {@code
 * InputResponseRequestParams} and whose responses admit {@code InputRequiredResult}. Hooking
 * anywhere wider (e.g. {@code DefaultMcpServer}) would cover methods that may not return {@code
 * input_required}; hooking anywhere narrower (per-handler interceptors) would miss the retry-path
 * params. The service-method seam is also the last frame that can return a value to the JSON-RPC
 * dispatcher before ripcurl's catch-all exception translation would swallow the internal {@link
 * InputRequiredException}.
 *
 * <p><strong>Call ordinals.</strong> Each {@code ctx.elicit(...)} call site is identified by its
 * call ordinal — the Nth elicit reached during execution maps to ledger position N. Answered
 * ordinals return their {@link ElicitResult} immediately; the first unanswered ordinal raises
 * {@link InputRequiredException}, which {@link #execute} converts into an {@code
 * InputRequiredResult} whose {@code requestState} folds in everything answered so far. A
 * fingerprint of each elicitation (message + schema) is stored per ledger slot; a replay that asks
 * a different question at an answered position violates the idempotency contract and is rejected
 * ({@link ElicitationLedgerMismatchException} → {@code -32602}).
 *
 * <p><strong>Decline/cancel.</strong> A {@code decline}/{@code cancel} {@link ElicitResult} is an
 * answer like any other: it is recorded in the ledger and returned to the handler, which decides
 * what to do ({@code ElicitResult.isAccepted()} semantics are unchanged).
 */
public class MrtrElicitationEngine implements ElicitationDispatcher {

  private final RequestStateCodec codec;
  private final ObjectMapper objectMapper;
  private final McpPrincipalSource principalSource;
  private final ReplayExecutor replayExecutor;

  public MrtrElicitationEngine(RequestStateCodec codec, ObjectMapper objectMapper) {
    this(codec, objectMapper, () -> null);
  }

  public MrtrElicitationEngine(
      RequestStateCodec codec, ObjectMapper objectMapper, McpPrincipalSource principalSource) {
    this(codec, objectMapper, principalSource, new ReplayExecutor(objectMapper));
  }

  /**
   * Primary constructor (ADR-0039): the {@link ReplayExecutor} is constructor-injected rather than
   * {@code new}'d internally, so a shared instance can also back a detached carrier's {@code
   * ToolCallReplayInvoker} (e.g. {@code ToolInvocationCore}) without going through this engine.
   */
  public MrtrElicitationEngine(
      RequestStateCodec codec,
      ObjectMapper objectMapper,
      McpPrincipalSource principalSource,
      ReplayExecutor replayExecutor) {
    this.codec = Objects.requireNonNull(codec, "codec");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.principalSource = Objects.requireNonNull(principalSource, "principalSource");
    this.replayExecutor = Objects.requireNonNull(replayExecutor, "replayExecutor");
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
    return replayExecutor.elicit(params);
  }

  /**
   * Runs one MRTR round trip for the given method: builds the response ledger from the retry's
   * {@code requestState}/{@code inputResponses} (empty on a fresh request), invokes the handler
   * with the ledger in scope, and converts a pending elicitation into an {@code
   * InputRequiredResult}.
   *
   * @param method the JSON-RPC method ({@code tools/call}, {@code prompts/get}, or {@code
   *     resources/read})
   * @param requestParams the request's typed params record; serialized and stripped of {@code
   *     _meta}/{@code inputResponses}/{@code requestState} to form the {@code originalParams}
   *     folded into the token
   * @param inputResponses the retry's answers, keyed by the {@code inputRequests} keys the server
   *     issued ({@code null} or empty on a fresh request)
   * @param requestState the opaque token from the previous round trip ({@code null} on a fresh
   *     request)
   * @param invocation invokes the handler from the top
   * @return the handler's result, or an {@code InputRequiredResult} if it elicited an unanswered
   *     question
   */
  public Object execute(
      String method,
      Object requestParams,
      Map<String, InputResponse> inputResponses,
      String requestState,
      Supplier<Object> invocation) {
    ObjectNode originalParams = originalParamsOf(requestParams);
    List<ResponseLedgerEntry> ledger =
        ledgerFor(method, originalParams, inputResponses, requestState);
    try {
      ReplayOutcome<Object, ElicitRequestFormParams> outcome =
          replayExecutor.execute(ledger, invocation);
      return switch (outcome) {
        case ReplayOutcome.InputRequired<Object, ElicitRequestFormParams>(
                var key,
                var request,
                var ledgerAtUnwind) -> {
          String token =
              codec.encode(
                  method, originalParams, ledgerAtUnwind, principalSource.currentPrincipal());
          yield new InputRequiredResult(
              Map.of(key, new ElicitRequest(request)), token, ResultTypes.INPUT_REQUIRED);
        }
        case ReplayOutcome.Completed<Object, ElicitRequestFormParams>(var result) -> result;
      };
    } catch (ElicitationLedgerMismatchException e) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, e.getMessage());
    }
  }

  private ObjectNode originalParamsOf(Object requestParams) {
    ObjectNode node = (ObjectNode) objectMapper.valueToTree(requestParams);
    node.remove("_meta");
    node.remove("inputResponses");
    node.remove("requestState");
    return node;
  }

  private List<ResponseLedgerEntry> ledgerFor(
      String method,
      ObjectNode originalParams,
      Map<String, InputResponse> inputResponses,
      String requestState) {
    if (requestState == null) {
      if (inputResponses != null && !inputResponses.isEmpty()) {
        throw invalidParams(
            "inputResponses present without requestState; retries must echo the requestState "
                + "from the InputRequiredResult");
      }
      return List.of();
    }
    RequestStatePayload payload = decode(requestState);
    verifySamePrincipal(payload.principal());
    if (!method.equals(payload.method())) {
      throw invalidParams(
          String.format(
              "requestState was issued for %s but the retry arrived on %s",
              payload.method(), method));
    }
    verifySameTarget(payload.originalParams(), originalParams);
    List<ResponseLedgerEntry> entries = new ArrayList<>(payload.inputResponses());
    if (inputResponses != null) {
      inputResponses.forEach((key, response) -> answer(entries, key, response));
    }
    return entries;
  }

  private RequestStatePayload decode(String requestState) {
    try {
      return codec.decode(requestState);
    } catch (InvalidRequestStateException e) {
      // Tampered, foreign-key, malformed, or expired state: never replay against it.
      throw new JsonRpcException(
          JsonRpcProtocol.INVALID_PARAMS, "Invalid requestState: " + e.getMessage(), e);
    }
  }

  private void verifySamePrincipal(String storedPrincipal) {
    if (!Objects.equals(principalSource.currentPrincipal(), storedPrincipal)) {
      throw invalidParams(
          "requestState was issued for a different principal; it cannot be replayed by another "
              + "caller");
    }
  }

  private void verifySameTarget(JsonNode stored, JsonNode incoming) {
    if (!Objects.equals(stored.path("name"), incoming.path("name"))
        || !Objects.equals(stored.path("uri"), incoming.path("uri"))) {
      throw invalidParams(
          "requestState was issued for a different target; retries must re-send the original "
              + "request parameters");
    }
  }

  private void answer(List<ResponseLedgerEntry> entries, String key, InputResponse response) {
    for (int i = 0; i < entries.size(); i++) {
      ResponseLedgerEntry entry = entries.get(i);
      if (entry.key().equals(key)) {
        if (entry.isAnswered()) {
          throw invalidParams(
              String.format(
                  "inputResponses key \"%s\" was already answered in a previous round trip", key));
        }
        if (!(response instanceof ElicitResult result)) {
          throw invalidParams(
              String.format(
                  "inputResponses key \"%s\" must carry an elicitation result; the server issued "
                      + "an elicitation/create request for it",
                  key));
        }
        entries.set(i, entry.answeredWith(result));
        return;
      }
    }
    throw invalidParams(
        String.format(
            "Unknown inputResponses key \"%s\"; the server never issued an input request with "
                + "that key",
            key));
  }

  private static JsonRpcException invalidParams(String message) {
    return new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, message);
  }
}
