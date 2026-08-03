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
package com.callibrity.mocapi.examples.tasksredis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The tasks example backed by a real Redis {@code TaskStore}. Spring Boot's Docker Compose support
 * starts the Redis in {@code compose.yaml} and contributes its connection details; substrate-redis
 * builds its Atom SPI from the resulting {@code RedisConnectionFactory}; and {@code
 * mocapi-tasks-substrate}'s autoconfiguration swaps the in-memory {@code TaskStore} for the
 * Substrate-backed one — making task state durable across application restarts. Kill the app at
 * {@code input_required}, restart it, answer via {@code tasks/update}, and MRTR replay completes
 * the task. See the module README for the walkthrough.
 */
@SpringBootApplication
public class TasksRedisExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(TasksRedisExampleApplication.class, args);
  }
}
