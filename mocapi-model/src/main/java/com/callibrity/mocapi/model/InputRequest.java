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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A server-initiated request embedded in {@link InputRequiredResult#inputRequests()}, discriminated
 * by its {@code method} literal. mocapi only ever emits {@link ElicitRequest}; the deprecated
 * sampling/roots members exist for 1:1 union fidelity with the spec.
 */
// SEP-2577 spec contract: the InputRequest union includes the deprecated sampling/roots request
// envelopes, which the spec retains for at least twelve months; referencing them here is required
// for 1:1 union completeness.
@SuppressWarnings("deprecation")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CreateMessageRequest.class, name = "sampling/createMessage"),
  @JsonSubTypes.Type(value = ListRootsRequest.class, name = "roots/list"),
  @JsonSubTypes.Type(value = ElicitRequest.class, name = "elicitation/create")
})
public sealed interface InputRequest
    permits CreateMessageRequest, ListRootsRequest, ElicitRequest {}
