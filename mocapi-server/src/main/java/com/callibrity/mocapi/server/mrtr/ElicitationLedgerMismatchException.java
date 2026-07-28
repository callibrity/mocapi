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
package com.callibrity.mocapi.server.mrtr;

/**
 * Raised when a replayed handler violates the MRTR idempotency contract (ADR-0021): the elicit call
 * reaching ledger position N issued a different question (message/schema fingerprint) than the one
 * position N answers. {@link MrtrElicitationEngine#execute} converts it into JSON-RPC {@code
 * -32602} (Invalid params) — replaying against the wrong answers would feed a handler data it never
 * asked for.
 *
 * <p>Like {@link InputRequiredException}, generic handler-exception wrapping must rethrow this type
 * so the engine can see it.
 */
public final class ElicitationLedgerMismatchException extends RuntimeException {

  ElicitationLedgerMismatchException(String message) {
    super(message);
  }
}
