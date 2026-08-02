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
package com.callibrity.mocapi.server.routing;

import java.util.Map;

/**
 * Contributes additional {@code Mcp-Name} routing-header validation entries for extension-defined
 * JSON-RPC methods.
 *
 * <p>The Streamable HTTP transport requires the {@code Mcp-Name} header to mirror a specific field
 * of {@code params} for certain methods (e.g. {@code tools/call} → {@code params.name}). The
 * built-in table lives in the transport module; this SPI lets other modules — such as {@code
 * mocapi-tasks}, which routes {@code tasks/get}, {@code tasks/update}, and {@code tasks/cancel}
 * against {@code params.taskId} — extend that table without the transport module depending on them.
 *
 * <p>This contract is transport-agnostic and lives in {@code mocapi-server} so it can be
 * implemented by any module; transports that don't validate routing headers (e.g. stdio) simply
 * ignore contributed instances.
 */
@FunctionalInterface
public interface McpRoutedParamContributor {

  /**
   * The methods this contributor adds to the {@code Mcp-Name} validation table.
   *
   * @return a map of JSON-RPC method name to the {@code params} field name that {@code Mcp-Name}
   *     must mirror
   */
  Map<String, String> namedParamFields();
}
