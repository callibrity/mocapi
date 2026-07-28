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
package com.callibrity.mocapi.server.progress;

import com.callibrity.mocapi.api.progress.DoubleProgressEmitter;

/** Default {@link DoubleProgressEmitter} backed by a single-operation {@link ProgressChannel}. */
class DefaultDoubleProgressEmitter implements DoubleProgressEmitter {

  private final ProgressChannel channel;

  DefaultDoubleProgressEmitter(ProgressChannel channel) {
    this.channel = channel;
  }

  @Override
  public void emit(double progress, String message) {
    channel.emit(progress, message);
  }

  @Override
  public void emit(double progress) {
    channel.emit(progress, null);
  }
}
