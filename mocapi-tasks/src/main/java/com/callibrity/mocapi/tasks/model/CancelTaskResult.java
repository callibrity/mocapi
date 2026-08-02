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
 * Result of a {@code tasks/cancel} request. Always carries {@link
 * com.callibrity.mocapi.model.ResultTypes#COMPLETE}: acceptance of the cancellation request is
 * synchronous even though the task's own transition to {@code cancelled} may lag.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelTaskResult(String resultType) {}
