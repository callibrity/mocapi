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
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The response to a {@code subscriptions/listen} request, signalling that the subscription has
 * ended gracefully (e.g. during server shutdown). Both the spec-required {@code resultType} and the
 * {@code _meta} envelope (carrying {@code io.modelcontextprotocol/subscriptionId}) are present; the
 * result body is otherwise empty. mocapi does not implement subscriptions (ADR-0022) and never
 * sends this; the type exists for 1:1 model fidelity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionsListenResult(
    @JsonProperty("_meta") SubscriptionsListenResultMetaObject meta, String resultType) {}
