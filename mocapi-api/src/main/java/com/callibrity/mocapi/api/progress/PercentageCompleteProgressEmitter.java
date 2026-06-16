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
package com.callibrity.mocapi.api.progress;

/**
 * Emits fraction-complete progress notifications for one operation (ADR-0025). Created from {@link
 * McpProgressSource#percentProgress()}, which fixes the wire {@code total} at {@code 1.0}; each
 * call reports the absolute fraction complete as a value in {@code [0.0, 1.0]}.
 *
 * <p>The MCP spec requires progress to strictly increase with each notification, so each {@code
 * complete(...)} call must be greater than the previous one or the emitter throws {@link
 * IllegalArgumentException}; a fraction outside {@code [0.0, 1.0]} is likewise rejected. The checks
 * run whether or not the client supplied a progress token; the token only governs whether a
 * notification is actually sent (no token ⇒ validated and discarded).
 */
public interface PercentageCompleteProgressEmitter {

  /**
   * Reports the absolute fraction complete with a human-readable message.
   *
   * @param fraction the fraction complete in {@code [0.0, 1.0]}; must exceed the previous value
   * @param message human-readable detail for this step (the spec's {@code message} field), or
   *     {@code null} to omit it
   * @throws IllegalArgumentException if {@code fraction} is outside {@code [0.0, 1.0]} or does not
   *     exceed the previous value
   */
  void complete(double fraction, String message);

  /**
   * Reports the absolute fraction complete with no message.
   *
   * @param fraction the fraction complete in {@code [0.0, 1.0]}; must exceed the previous value
   * @throws IllegalArgumentException if {@code fraction} is outside {@code [0.0, 1.0]} or does not
   *     exceed the previous value
   */
  void complete(double fraction);
}
