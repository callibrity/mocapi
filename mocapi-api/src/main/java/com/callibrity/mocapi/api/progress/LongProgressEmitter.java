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
package com.callibrity.mocapi.api.progress;

/**
 * Emits integer-valued progress notifications for one operation (ADR-0025). Created from {@link
 * McpProgressSource#longProgress(Long)}, which captures the total once; each call reports the
 * absolute progress value so far. Values are emitted as whole-number JSON (the spec's {@code
 * number} type permits integers as well as floating point).
 *
 * <p>The MCP spec requires progress to strictly increase with each notification, so each {@code
 * emit(...)} call must be greater than the previous one or the emitter throws {@link
 * IllegalArgumentException}. The check runs whether or not the client supplied a progress token;
 * the token only governs whether a notification is actually sent (no token ⇒ validated and
 * discarded).
 */
public interface LongProgressEmitter {

  /**
   * Emits absolute progress with a human-readable message.
   *
   * @param progress the progress so far; must exceed the previously reported value
   * @param message human-readable detail for this step (the spec's {@code message} field), or
   *     {@code null} to omit it
   * @throws IllegalArgumentException if {@code progress} does not exceed the previous value
   */
  void emit(long progress, String message);

  /**
   * Emits absolute progress with no message.
   *
   * @param progress the progress so far; must exceed the previously reported value
   * @throws IllegalArgumentException if {@code progress} does not exceed the previous value
   */
  void emit(long progress);
}
