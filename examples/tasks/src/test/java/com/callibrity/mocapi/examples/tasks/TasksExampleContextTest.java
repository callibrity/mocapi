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
package com.callibrity.mocapi.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the real application context to prove the leanest wiring works: {@link TaskDemoTools} is a
 * plain {@code @Component}, component-scanned and discovered by mocapi's handler scan, and
 * {@code @McpTask} is recognized once {@code mocapi-tasks} is on the classpath — no
 * example-specific auto-configuration.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest
class TasksExampleContextTest {

  @Autowired private McpToolsService tools;

  @Test
  void the_task_demo_tools_are_discovered_via_component_scan() {
    assertThat(tools.listTools(null).tools())
        .extracting(Tool::name)
        .contains("batch_resize", "confirmed_report", "must_run_as_task");
  }
}
