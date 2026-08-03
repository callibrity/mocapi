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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** Tests for {@link TaskIds} — 256-bit Base64URL task identifiers. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskIdsTest {

  @Test
  void newTaskId_is_43_char_base64url_without_padding() {
    String id = TaskIds.newTaskId();

    assertThat(id).hasSize(43).matches("[A-Za-z0-9_-]+").doesNotContain("=");
  }

  @Test
  void newTaskId_produces_1000_distinct_ids() {
    Set<String> ids = new HashSet<>();
    IntStream.range(0, 1000).forEach(i -> ids.add(TaskIds.newTaskId()));

    assertThat(ids).hasSize(1000);
  }
}
