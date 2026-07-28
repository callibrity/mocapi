/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Extends the spec's {@code InputResponseRequestParams}: an MRTR retry of {@code tools/call}
 * re-sends the original params plus {@code inputResponses} (keyed identically to the server's
 * {@code inputRequests} map) and the opaque {@code requestState}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallToolRequestParams(
    String name,
    JsonNode arguments,
    Map<String, InputResponse> inputResponses,
    String requestState,
    @JsonProperty("_meta") RequestMeta meta) {}
