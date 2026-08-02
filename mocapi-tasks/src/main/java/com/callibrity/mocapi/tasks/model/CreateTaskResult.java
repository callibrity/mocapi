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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The immediate {@code tools/call} response when the call is dispatched as a task: acknowledges
 * task creation without waiting for the underlying tool handler to complete.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateTaskResult(
    String taskId,
    TaskStatus status,
    String statusMessage,
    String createdAt,
    String lastUpdatedAt,
    Long ttlMs,
    Long pollIntervalMs,
    String resultType) {}
