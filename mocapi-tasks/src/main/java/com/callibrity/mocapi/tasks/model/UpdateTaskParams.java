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

import com.callibrity.mocapi.model.RequestMeta;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Params of a {@code tasks/update} request: resumes a task that reached {@code input_required} by
 * supplying the client's responses to its pending {@link com.callibrity.mocapi.model.InputRequest
 * input requests}.
 *
 * <p>{@code inputResponses} values are kept as raw {@link JsonNode} rather than the sealed {@link
 * com.callibrity.mocapi.model.InputResponse} union deliberately: that union has no wire
 * discriminator (deduction-based, per spec) and a malformed or unrecognized-shape entry would fail
 * eager binding for the *whole* request, rather than being ignored per SEP-2322's "servers SHOULD
 * ignore information they do not recognize." {@link McpTasksService} converts each entry leniently,
 * per outstanding key, so one bad entry can never break the others or the ack.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateTaskParams(
    String taskId, Map<String, JsonNode> inputResponses, @JsonProperty("_meta") RequestMeta meta) {}
