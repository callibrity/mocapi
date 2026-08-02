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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.callibrity.mocapi.model.ServerCapabilities;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TasksCapabilityCustomizerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void declares_the_tasks_extension_as_an_empty_object() {
    ServerCapabilities.Builder builder = ServerCapabilities.builder();

    new TasksCapabilityCustomizer(mapper).customize(builder);

    ServerCapabilities capabilities = builder.build();
    assertThat(capabilities.extensions()).containsKey(TasksExtension.EXTENSION_ID);
    assertThat(capabilities.extensions().get(TasksExtension.EXTENSION_ID).isEmpty()).isTrue();
  }

  @Test
  void routed_param_contributor_maps_all_three_task_methods_to_task_id() {
    var contributor = new TasksRoutedParamContributor();

    var fields = contributor.namedParamFields();

    assertThat(fields)
        .containsOnly(
            entry(TasksExtension.TASKS_GET, "taskId"),
            entry(TasksExtension.TASKS_UPDATE, "taskId"),
            entry(TasksExtension.TASKS_CANCEL, "taskId"));
  }
}
