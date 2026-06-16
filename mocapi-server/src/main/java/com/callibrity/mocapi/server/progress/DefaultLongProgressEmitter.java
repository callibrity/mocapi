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

import com.callibrity.mocapi.api.progress.LongProgressEmitter;

/**
 * Default {@link LongProgressEmitter} backed by a single-operation {@link ProgressChannel}. Integer
 * values are emitted as whole-number JSON on the wire.
 */
class DefaultLongProgressEmitter implements LongProgressEmitter {

  private final ProgressChannel channel;

  DefaultLongProgressEmitter(ProgressChannel channel) {
    this.channel = channel;
  }

  @Override
  public void emit(long progress, String message) {
    channel.emit(progress, message);
  }

  @Override
  public void emit(long progress) {
    channel.emit(progress, null);
  }
}
