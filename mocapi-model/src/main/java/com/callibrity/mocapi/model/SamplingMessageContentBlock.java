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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Content block union for {@link SamplingMessage}: the shared text/image/audio blocks plus the
 * sampling-only {@link ToolUseContent} and {@link ToolResultContent}.
 *
 * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577) along with the sampling
 *     feature; remains in the specification for at least twelve months.
 */
@Deprecated(since = "2026-07-28")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TextContent.class, name = "text"),
  @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
  @JsonSubTypes.Type(value = AudioContent.class, name = "audio"),
  @JsonSubTypes.Type(value = ToolUseContent.class, name = "tool_use"),
  @JsonSubTypes.Type(value = ToolResultContent.class, name = "tool_result")
})
public sealed interface SamplingMessageContentBlock
    permits TextContent, ImageContent, AudioContent, ToolUseContent, ToolResultContent {}
