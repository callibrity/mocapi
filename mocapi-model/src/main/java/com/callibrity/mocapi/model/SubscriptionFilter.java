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
import java.util.List;

/**
 * The set of notification types a client may opt in to on a {@code subscriptions/listen} request.
 * Each type is opt-in; the server MUST NOT send notification types the client has not requested.
 * {@code resourceSubscriptions} replaces the former {@code resources/subscribe} RPC. mocapi does
 * not implement subscriptions (ADR-0022); this type exists for 1:1 model fidelity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionFilter(
    Boolean toolsListChanged,
    Boolean promptsListChanged,
    Boolean resourcesListChanged,
    List<String> resourceSubscriptions) {}
