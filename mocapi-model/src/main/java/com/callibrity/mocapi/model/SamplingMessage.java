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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/**
 * A message in a sampling conversation. The spec's {@code content} is {@code
 * SamplingMessageContentBlock | SamplingMessageContentBlock[]}; modeled as a list that also accepts
 * the single-block wire form on deserialization.
 *
 * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577) along with the sampling
 *     feature; remains in the specification for at least twelve months. The spec's suggested
 *     migration is for clients to perform sampling through their own provider APIs.
 */
@Deprecated(since = "2026-07-28")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SamplingMessage(
    Role role,
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<SamplingMessageContentBlock> content,
    @JsonProperty("_meta") ObjectNode meta) {}
