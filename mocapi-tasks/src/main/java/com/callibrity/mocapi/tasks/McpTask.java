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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool-handler method as eligible for task-augmented invocation under the {@code
 * io.modelcontextprotocol/tasks} extension: when the client declares the {@code
 * io.modelcontextprotocol/tasks} extension in the per-request {@code _meta} client capabilities,
 * the server returns a {@code CreateTaskResult} immediately and the client polls for completion via
 * {@code tasks/get}, instead of waiting synchronously for the handler to return. A client that does
 * not declare the extension gets ordinary synchronous execution, unless {@link #required()} is set,
 * in which case the call is rejected with JSON-RPC {@code -32021}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface McpTask {

  /**
   * ISO-8601 duration the task record is retained after reaching a terminal status. Empty string
   * defers to the {@code mocapi.tasks.default-ttl} property (default {@code PT1H}).
   */
  String ttl() default "";

  /**
   * ISO-8601 duration a client should wait between {@code tasks/get} polls. Empty string defers to
   * the {@code mocapi.tasks.default-poll-interval} property (default {@code PT2S}).
   */
  String pollInterval() default "";

  /**
   * When {@code true}, clients that don't declare the {@code tasks} capability are rejected with
   * JSON-RPC {@code -32021} rather than falling back to synchronous execution.
   */
  boolean required() default false;
}
