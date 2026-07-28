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
package com.callibrity.mocapi.model;

/**
 * Spec-defined {@code _meta} key strings (MCP 2026-07-28). The three {@code
 * io.modelcontextprotocol/*}-prefixed request keys are REQUIRED on every client request's {@code
 * _meta} envelope; the trace-context keys are explicit unprefixed exceptions defined in spec prose
 * (W3C Trace Context / Baggage formats).
 */
public final class McpMetaKeys {

  /** Protocol version the client is speaking. Required on every client request. */
  public static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";

  /** Client implementation info ({@link Implementation}). Required on every client request. */
  public static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";

  /** Client capabilities ({@link ClientCapabilities}). Required on every client request. */
  public static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";

  /**
   * Subscription identifier the server MUST include in the {@code _meta} of notifications delivered
   * on a {@code subscriptions/listen} stream. Present for model completeness; mocapi does not
   * implement subscriptions (ADR-0022).
   */
  public static final String SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";

  /** Progress token for {@code notifications/progress} correlation (unprefixed). */
  public static final String PROGRESS_TOKEN = "progressToken";

  /** W3C Trace Context {@code traceparent} (unprefixed exception to the prefix rule). */
  public static final String TRACEPARENT = "traceparent";

  /** W3C Trace Context {@code tracestate} (unprefixed exception to the prefix rule). */
  public static final String TRACESTATE = "tracestate";

  /** W3C Baggage {@code baggage} (unprefixed exception to the prefix rule). */
  public static final String BAGGAGE = "baggage";

  /**
   * Server implementation info ({@link Implementation}). Servers SHOULD include this in the {@code
   * _meta} of every successful response.
   */
  public static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

  private McpMetaKeys() {}
}
