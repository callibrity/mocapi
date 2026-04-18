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
package com.callibrity.mocapi.transport.http.writer;

import com.callibrity.ripcurl.core.JsonRpcMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ClosedMessageWriter implements MessageWriter {
  public static final MessageWriter INSTANCE = new ClosedMessageWriter();

  @Override
  public MessageWriter write(JsonRpcMessage msg) {
    log.warn("Rejected write to Closed writer ({})", msg.getClass().getSimpleName());
    throw new IllegalStateException("Cannot write message to a closed response.");
  }
}
