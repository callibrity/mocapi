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
package com.callibrity.mocapi.logging;

/**
 * MDC key constants stamped by {@link McpMdcInterceptor}. The values are stable strings suitable
 * for log-grep and alert rules.
 */
public final class McpMdcKeys {

  /** Current MCP session id; only set when a session is bound to the invocation. */
  public static final String SESSION = "mcp.session";

  /**
   * Handler kind — one of {@code tool} / {@code prompt} / {@code resource} / {@code
   * resource_template}.
   */
  public static final String HANDLER_KIND = "mcp.handler.kind";

  /** Handler name — tool/prompt name, or resource URI / resource-template URI template. */
  public static final String HANDLER_NAME = "mcp.handler.name";

  /**
   * JSON-RPC request id. Reserved; not yet populated. A later spec wires a per-invocation scoped
   * value that the interceptor can read to set this key.
   */
  public static final String REQUEST = "mcp.request";

  private McpMdcKeys() {}
}
