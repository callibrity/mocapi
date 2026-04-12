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
package com.callibrity.mocapi.server;

import com.callibrity.ripcurl.core.JsonRpcMessage;
import java.util.ArrayList;
import java.util.List;

/** Test utility that captures sent messages and emitted events. */
public class CapturingTransport implements McpTransport {

  private final List<McpEvent> events = new ArrayList<>();
  private final List<JsonRpcMessage> messages = new ArrayList<>();

  @Override
  public void emit(McpEvent event) {
    events.add(event);
  }

  @Override
  public void send(JsonRpcMessage message) {
    messages.add(message);
  }

  public List<McpEvent> events() {
    return List.copyOf(events);
  }

  public List<JsonRpcMessage> messages() {
    return List.copyOf(messages);
  }
}
