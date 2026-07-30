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
 * Creates typed progress emitters for the current handler invocation (ADR-0025). A handler reaches
 * this surface through its {@code MrtrContext} (tool, prompt, or resource); each factory captures
 * the operation's total once and returns a stateful emitter that reports absolute progress.
 *
 * <p>Replaces the former {@code McpToolContext.sendProgress(long, long)}, widening the API to the
 * spec's full progress notification shape: floating-point {@code progress}/{@code total}, the
 * human-readable {@code message} field, and an enforced strictly-increasing contract.
 *
 * <pre>{@code
 * var p = ctx.percentProgress();
 * p.complete(0.25, "a quarter");
 * p.complete(0.5);
 * p.complete(1.0, "done");
 * }</pre>
 *
 * <p>When the client did not supply a progress token, the returned emitter still validates each
 * call but sends nothing — so a non-increasing-progress bug surfaces identically regardless of
 * whether a particular client requested progress.
 */
public interface McpProgressSource {

  /**
   * Returns an emitter for floating-point progress.
   *
   * @param total the total expected progress, or {@code null} if unknown (omitted on the wire)
   * @return a stateful double-valued progress emitter
   */
  DoubleProgressEmitter doubleProgress(Double total);

  /**
   * Returns an emitter for integer-valued progress.
   *
   * @param total the total expected progress, or {@code null} if unknown (omitted on the wire)
   * @return a stateful long-valued progress emitter
   */
  LongProgressEmitter longProgress(Long total);

  /**
   * Returns an emitter that advances progress by one on each call (reporting 1, 2, 3, …) — the
   * loop-friendly form that needs no caller-managed counter.
   *
   * @param total the total expected number of steps, or {@code null} if unknown (omitted on the
   *     wire)
   * @return a stateful counting progress emitter
   */
  CountingProgressEmitter countingProgress(Long total);

  /**
   * Returns an emitter for fraction-complete progress in {@code [0.0, 1.0]}, reported on the wire
   * against a fixed {@code total} of {@code 1.0}.
   *
   * @return a stateful percentage-complete progress emitter
   */
  PercentageCompleteProgressEmitter percentProgress();
}
