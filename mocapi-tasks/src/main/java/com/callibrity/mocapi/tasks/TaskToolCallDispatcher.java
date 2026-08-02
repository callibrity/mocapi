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

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.ToolCallDispatchCustomizer;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.engine.TaskIds;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Reroutes {@code @McpTask}-annotated {@code tools/call} invocations to the {@link
 * TaskExecutionEngine} instead of the default synchronous MRTR path, implementing the decision rule
 * of the {@code io.modelcontextprotocol/tasks} extension: no annotation never becomes a task; an
 * annotated handler becomes a task only when the client declared the {@code tasks} capability;
 * {@code required = true} rejects a non-capable client with {@code -32021} instead of degrading to
 * synchronous execution.
 */
public class TaskToolCallDispatcher implements ToolCallDispatchCustomizer {

  /**
   * Fallback {@code ttl}/{@code pollInterval} for {@code @McpTask} handlers that leave either
   * annotation attribute blank, sourced from {@code mocapi.tasks.*} properties.
   */
  public record Defaults(Duration ttl, Duration pollInterval) {}

  private final TaskExecutionEngine engine;
  private final McpPrincipalSource principalSource;
  private final ObjectMapper objectMapper;
  private final Defaults defaults;
  private final Clock clock;

  public TaskToolCallDispatcher(
      TaskExecutionEngine engine,
      McpPrincipalSource principalSource,
      ObjectMapper objectMapper,
      Defaults defaults,
      Clock clock) {
    this.engine = engine;
    this.principalSource = principalSource;
    this.objectMapper = objectMapper;
    this.defaults = defaults;
    this.clock = clock;
  }

  @Override
  public Optional<Object> dispatch(CallToolHandler handler, CallToolRequestParams params) {
    McpTask annotation =
        AnnotatedElementUtils.findMergedAnnotation(handler.method(), McpTask.class);
    if (annotation == null) {
      return Optional.empty(); // never a task
    }
    if (!isTaskCapable(params.meta())) {
      if (annotation.required()) {
        throw new McpTaskRequiredException(handler.name());
      }
      return Optional.empty(); // graceful sync degrade
    }
    TaskRecord record = newRecord(handler, params, annotation);
    return Optional.of(engine.createAndStart(record));
  }

  static boolean isTaskCapable(RequestMeta meta) {
    return meta != null
        && meta.clientCapabilities() != null
        && meta.clientCapabilities().extensions() != null
        && meta.clientCapabilities().extensions().containsKey(TasksExtension.EXTENSION_ID);
  }

  private TaskRecord newRecord(
      CallToolHandler handler, CallToolRequestParams params, McpTask annotation) {
    RequestMeta meta = params.meta();
    Instant now = clock.instant();
    return new TaskRecord(
        TaskIds.newTaskId(),
        handler.name(),
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode(),
        principalSource.currentPrincipal(),
        meta.protocolVersion(),
        meta.clientCapabilities(),
        TaskStatus.WORKING,
        null,
        now,
        now,
        resolveDuration(annotation.ttl(), defaults.ttl()),
        resolveDuration(annotation.pollInterval(), defaults.pollInterval()),
        List.of(),
        Map.of(),
        null,
        null,
        0L);
  }

  private static Duration resolveDuration(String value, Duration fallback) {
    return value.isBlank() ? fallback : Duration.parse(value);
  }
}
