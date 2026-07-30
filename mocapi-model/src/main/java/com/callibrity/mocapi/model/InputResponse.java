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
 * A client response to a server-initiated {@link InputRequest}, carried in the retried request's
 * {@code inputResponses} map. The spec union has no discriminator property; members are deduced
 * from their property fingerprints ({@code action} for {@link ElicitResult}, {@code roots} for
 * {@link ListRootsResult}, {@code role}/{@code model} for {@link CreateMessageResult}).
 */
// SEP-2577 spec contract: the InputResponse union includes the deprecated sampling/roots result
// shapes, which the spec retains for at least twelve months; referencing them here is required
// for 1:1 union completeness.
@SuppressWarnings("deprecation")
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(CreateMessageResult.class),
  @JsonSubTypes.Type(ListRootsResult.class),
  @JsonSubTypes.Type(ElicitResult.class)
})
public sealed interface InputResponse permits CreateMessageResult, ListRootsResult, ElicitResult {}
