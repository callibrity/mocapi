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
package com.callibrity.mocapi.tasks.engine;

import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.server.progress.DefaultMcpProgressSource;
import com.callibrity.mocapi.tasks.store.TaskStore;
import java.time.Clock;

/**
 * Factory for {@link McpProgressSource} instances that route progress emits to a task's
 * statusMessage.
 */
public final class TaskProgressSource {
  private TaskProgressSource() {}

  /**
   * McpProgressSource whose emits write the task's statusMessage. Format: with total:
   * "<progress>/<total>" ; without: "<progress>" ; message appended as ": <message>".
   */
  public static McpProgressSource forTask(TaskStore store, String taskId, Clock clock) {
    return new DefaultMcpProgressSource(
        (progress, total, message) -> {
          String label = total != null ? progress + "/" + total : String.valueOf(progress);
          String statusMessage = message != null ? label + ": " + message : label;
          store.update(taskId, r -> r.withStatusMessage(statusMessage, clock.instant()));
        });
  }
}
