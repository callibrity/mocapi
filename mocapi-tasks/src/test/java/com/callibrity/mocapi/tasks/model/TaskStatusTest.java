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
package com.callibrity.mocapi.tasks.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskStatusTest {

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void terminal_statuses_report_terminal(TaskStatus status) {
    assertThat(status.isTerminal()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void non_terminal_statuses_report_not_terminal(TaskStatus status) {
    assertThat(status.isTerminal()).isFalse();
  }
}
