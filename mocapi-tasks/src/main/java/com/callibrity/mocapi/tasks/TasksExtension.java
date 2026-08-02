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
 * Well-known identifiers for the {@code io.modelcontextprotocol/tasks} extension: the extension id
 * itself, its three JSON-RPC method names, and its {@code resultType} literal.
 */
public final class TasksExtension {

  public static final String EXTENSION_ID = "io.modelcontextprotocol/tasks";
  public static final String TASKS_GET = "tasks/get";
  public static final String TASKS_UPDATE = "tasks/update";
  public static final String TASKS_CANCEL = "tasks/cancel";
  public static final String RESULT_TYPE_TASK = "task";

  private TasksExtension() {}
}
