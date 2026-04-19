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
package com.callibrity.mocapi.server.guards;

/**
 * Outcome of a {@link Guard#check()} evaluation: either {@link Allow} (the handler is permitted for
 * the current caller) or {@link Deny} (the handler must be hidden from list operations and the call
 * rejected).
 */
public sealed interface GuardDecision {

  /** Permit the handler. */
  record Allow() implements GuardDecision {}

  /**
   * Reject the handler. The {@code reason} is surfaced in the JSON-RPC error message on call-time
   * denial; it is never exposed at list-time (denied handlers simply do not appear).
   */
  record Deny(String reason) implements GuardDecision {}
}
