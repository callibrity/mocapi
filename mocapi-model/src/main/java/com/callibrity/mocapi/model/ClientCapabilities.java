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
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

/**
 * Capabilities a client declares in the {@code io.modelcontextprotocol/clientCapabilities} {@code
 * _meta} field of every request. {@code extensions} keys are reverse-DNS extension identifiers
 * (SEP-2133). The {@code roots} and {@code sampling} members are deprecated (SEP-2577).
 */
// SEP-2577 spec contract: the deprecated roots/sampling capability members remain in the
// specification for the deprecation window; modeling them is required for 1:1 fidelity.
@SuppressWarnings("deprecation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCapabilities(
    Map<String, ObjectNode> experimental,
    @Deprecated RootsCapability roots,
    @Deprecated SamplingCapability sampling,
    ElicitationCapability elicitation,
    Map<String, ObjectNode> extensions) {}
