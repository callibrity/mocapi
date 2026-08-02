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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable mocapi example demonstrating the MCP Tasks extension ({@code
 * io.modelcontextprotocol/tasks}) over Streamable HTTP: a {@code @McpTask} tool that reports
 * progress, a {@code @McpTask} tool that elicits mid-execution (demonstrating {@code
 * input_required} → {@code tasks/update} → resume), and a {@code @McpTask(required = true)} tool
 * that rejects non-capable callers with {@code -32021}. See the module README.
 */
@SpringBootApplication
public class TasksExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(TasksExampleApplication.class, args);
  }
}
