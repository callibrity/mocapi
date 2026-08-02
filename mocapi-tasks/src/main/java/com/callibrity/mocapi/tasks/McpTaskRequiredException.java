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
package com.callibrity.mocapi.tasks;

/**
 * Thrown when a caller needs the {@code io.modelcontextprotocol/tasks} capability but did not
 * declare it — either a {@code tools/call} targeting an {@code @McpTask(required = true)} handler,
 * or any of the three {@code tasks/*} namespace methods, which SEP-2575/SEP-2663 gate on the same
 * capability regardless of a handler's {@code required} setting. Translated to JSON-RPC {@code
 * -32021} by {@link TaskRequiredExceptionTranslator}.
 */
public class McpTaskRequiredException extends RuntimeException {

  /**
   * @param subject a human-readable description of what needed the capability, e.g. {@code "Tool
   *     \"slow_compute\""} or {@code "Method \"tasks/get\""}
   */
  public McpTaskRequiredException(String subject) {
    super(subject + " requires the io.modelcontextprotocol/tasks client capability");
  }
}
