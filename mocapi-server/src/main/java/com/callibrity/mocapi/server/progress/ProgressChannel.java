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
package com.callibrity.mocapi.server.progress;

/**
 * The single-operation engine behind every progress emitter (ADR-0025). Tracks the last reported
 * value to enforce the spec's strictly-increasing contract, then hands each accepted update to a
 * {@link ProgressSink} for delivery.
 *
 * <p>Validation runs regardless of the sink, so a non-increasing bug surfaces the same way whether
 * the update ends up on the wire, in a task's {@code statusMessage}, or nowhere at all.
 */
class ProgressChannel {

  private final ProgressSink sink;
  private final Number total;
  private boolean started;
  private double last;

  ProgressChannel(ProgressSink sink, Number total) {
    this.sink = sink;
    this.total = total;
  }

  void emit(Number progress, String message) {
    double value = progress.doubleValue();
    if (started && value <= last) {
      throw new IllegalArgumentException(
          String.format(
              "progress must strictly increase with each notification: previous=%s, got=%s",
              last, value));
    }
    started = true;
    last = value;
    sink.accept(progress, total, message);
  }
}
