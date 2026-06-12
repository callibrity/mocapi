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

/**
 * The W3C trace-context values a client may carry in its request {@code _meta}: {@code
 * traceparent}, {@code tracestate}, and {@code baggage}. The spec defines them in prose ({@code
 * basic/index#meta}, "OpenTelemetry trace context") as unprefixed sibling keys of the {@code
 * io.modelcontextprotocol/*} envelope keys — an explicit exception to the {@code _meta} prefix
 * rule. Values are the W3C Trace Context / Baggage header strings verbatim; mocapi does not parse
 * or validate them here — the observability modules hand them to a propagator, which owns format
 * validation.
 *
 * @param traceparent the W3C {@code traceparent} header value, or {@code null} when absent
 * @param tracestate the W3C {@code tracestate} header value, or {@code null} when absent
 * @param baggage the W3C {@code baggage} header value, or {@code null} when absent
 */
public record TraceContext(String traceparent, String tracestate, String baggage) {

  /** The empty context: no trace keys present on the request. */
  public static final TraceContext NONE = new TraceContext(null, null, null);

  /**
   * Returns true when the client supplied a {@code traceparent} — the key that makes remote-parent
   * joining possible; {@code tracestate}/{@code baggage} are meaningless without it.
   */
  public boolean isPresent() {
    return traceparent != null;
  }
}
