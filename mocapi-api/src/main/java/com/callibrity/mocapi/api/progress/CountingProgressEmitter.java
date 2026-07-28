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
 * Emits step-counting progress notifications for one operation (ADR-0025). Created from {@link
 * McpProgressSource#countingProgress(Long)}; each call advances the progress by one (reporting 1,
 * 2, 3, …), so the strictly-increasing contract holds by construction and the caller never tracks a
 * counter. Ideal for loops that do one notification per item.
 *
 * <pre>{@code
 * var p = ctx.countingProgress((long) items.size());
 * for (var item : items) {
 *   process(item);
 *   p.emit("processed " + item.name());
 * }
 * }</pre>
 *
 * <p>As with the other emitters, when the client supplied no progress token the call is a no-op (no
 * counter advance is observable on the wire).
 */
public interface CountingProgressEmitter {

  /**
   * Advances progress by one and emits a notification with a human-readable message.
   *
   * @param message human-readable detail for this step (the spec's {@code message} field), or
   *     {@code null} to omit it
   */
  void emit(String message);

  /** Advances progress by one and emits a notification with no message. */
  void emit();
}
