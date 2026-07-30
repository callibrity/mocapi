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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A AudioContent content block.
 *
 * <p>SEP-2577 spec contract: AudioContent is also a member of the deprecated {@link
 * SamplingMessageContentBlock}} union, which the spec retains for at least twelve months.
 * Implementing it is required for 1:1 union completeness (ADR-0014).
 */
@SuppressWarnings("deprecation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AudioContent(String data, String mimeType, Annotations annotations)
    implements ContentBlock, SamplingMessageContentBlock {}
