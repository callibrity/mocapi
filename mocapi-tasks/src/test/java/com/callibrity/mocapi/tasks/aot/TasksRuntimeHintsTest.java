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
package com.callibrity.mocapi.tasks.aot;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.tasks.model.CancelTaskParams;
import com.callibrity.mocapi.tasks.model.CancelTaskResult;
import com.callibrity.mocapi.tasks.model.CreateTaskResult;
import com.callibrity.mocapi.tasks.model.GetTaskParams;
import com.callibrity.mocapi.tasks.model.GetTaskResult;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.model.UpdateTaskParams;
import com.callibrity.mocapi.tasks.model.UpdateTaskResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TasksRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  {
    new TasksRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void registers_hints_for_task_creation_result() {
    // The type behind the native-image gap this registrar exists to close: CreateTaskResult is the
    // immediate tools/call response when a call is dispatched as a task.
    assertTypeHintRegistered(CreateTaskResult.class);
  }

  @Test
  void registers_hints_for_task_lifecycle_results_and_params() {
    assertTypeHintRegistered(GetTaskResult.class);
    assertTypeHintRegistered(GetTaskParams.class);
    assertTypeHintRegistered(UpdateTaskResult.class);
    assertTypeHintRegistered(UpdateTaskParams.class);
    assertTypeHintRegistered(CancelTaskResult.class);
    assertTypeHintRegistered(CancelTaskParams.class);
  }

  @Test
  void registers_hints_for_the_task_status_enum() {
    assertTypeHintRegistered(TaskStatus.class);
  }

  private void assertTypeHintRegistered(Class<?> type) {
    assertThat(hints.reflection().typeHints())
        .as("expected binding hints for %s", type.getName())
        .anyMatch(th -> th.getType().equals(TypeReference.of(type)));
  }
}
