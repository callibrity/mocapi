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
package com.callibrity.mocapi.server.exchange;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;

/**
 * Immutable, per-request view of the client data carried in the request's {@code _meta} envelope
 * (ADR-0020). MCP 2026-07-28 has no sessions: every request is self-contained, so the exchange is
 * parsed fresh from each request by {@link MetaEnvelopeParser} and scoped to that single dispatch
 * via {@link #CURRENT}.
 *
 * @param protocolVersion the {@code io.modelcontextprotocol/protocolVersion} value
 * @param clientInfo the {@code io.modelcontextprotocol/clientInfo} value
 * @param clientCapabilities the {@code io.modelcontextprotocol/clientCapabilities} value
 * @param traceContext the optional unprefixed W3C trace-context keys ({@code traceparent} / {@code
 *     tracestate} / {@code baggage}); never {@code null} — {@link TraceContext#NONE} when the
 *     request carries none
 */
public record McpExchange(
    String protocolVersion,
    Implementation clientInfo,
    ClientCapabilities clientCapabilities,
    TraceContext traceContext) {

  public static final ScopedValue<McpExchange> CURRENT = ScopedValue.newInstance();

  public McpExchange {
    traceContext = traceContext == null ? TraceContext.NONE : traceContext;
  }

  /** Convenience constructor for exchanges without client-supplied trace context. */
  public McpExchange(
      String protocolVersion, Implementation clientInfo, ClientCapabilities clientCapabilities) {
    this(protocolVersion, clientInfo, clientCapabilities, TraceContext.NONE);
  }

  /**
   * Returns true if the client supports form-mode elicitation. Form support is implicit in the
   * spec: a bare {@code "elicitation": {}} capability declares a form-capable client, so the
   * capability's presence — not its {@code form} sub-object — is the signal.
   */
  public boolean supportsElicitationForm() {
    return clientCapabilities != null && clientCapabilities.elicitation() != null;
  }
}
