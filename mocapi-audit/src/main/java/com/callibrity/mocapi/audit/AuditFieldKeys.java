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
package com.callibrity.mocapi.audit;

/**
 * Structured-field key constants emitted by {@link AuditLoggingInterceptor}. Keys use {@code
 * snake_case} to match typical structured-log conventions (Splunk / ELK / Datadog dashboards)
 * rather than Java camelCase.
 */
public final class AuditFieldKeys {

  /** Caller identity — typically the authenticated principal name, or {@code anonymous}. */
  public static final String CALLER = "caller";

  /**
   * MCP protocol version declared in the request's {@code _meta} envelope; {@code null} when no
   * exchange is bound (e.g., outside a dispatch).
   */
  public static final String PROTOCOL_VERSION = "protocol_version";

  /**
   * Client name from the envelope's {@code io.modelcontextprotocol/clientInfo}; {@code null} when
   * no exchange is bound.
   */
  public static final String CLIENT_NAME = "client_name";

  /** One of {@code tool} / {@code prompt} / {@code resource} / {@code resource_template}. */
  public static final String HANDLER_KIND = "handler_kind";

  /** Tool / prompt name, or resource URI / URI template. */
  public static final String HANDLER_NAME = "handler_name";

  /** One of {@code success} / {@code forbidden} / {@code invalid_params} / {@code error}. */
  public static final String OUTCOME = "outcome";

  /** Wall-clock duration of the invocation in integer milliseconds. */
  public static final String DURATION_MS = "duration_ms";

  /**
   * SHA-256 hash (prefixed {@code sha256:}) of the canonicalized arguments. Emitted only when
   * {@code mocapi.audit.hash-arguments=true}.
   */
  public static final String ARGUMENTS_HASH = "arguments_hash";

  /** Simple name of the exception that caused an {@code invalid_params} / {@code error} outcome. */
  public static final String ERROR_CLASS = "error_class";

  private AuditFieldKeys() {}
}
