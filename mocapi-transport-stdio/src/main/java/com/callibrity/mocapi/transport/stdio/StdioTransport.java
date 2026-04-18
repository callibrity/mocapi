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
package com.callibrity.mocapi.transport.stdio;

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import java.io.PrintStream;
import java.util.function.Consumer;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP transport over stdio: every outbound JSON-RPC message is written to stdout as a single
 * newline-delimited JSON line. Thread-safety is inherited from {@link PrintStream}, which
 * synchronizes its own write operations — concurrent handler virtual threads cannot interleave
 * partial lines.
 */
public final class StdioTransport implements McpTransport {

  private final ObjectMapper objectMapper;
  private final PrintStream out;
  private final Consumer<String> sessionIdSink;

  public StdioTransport(
      ObjectMapper objectMapper, PrintStream out, Consumer<String> sessionIdSink) {
    this.objectMapper = objectMapper;
    this.out = out;
    this.sessionIdSink = sessionIdSink;
  }

  @Override
  public void send(JsonRpcMessage message) {
    out.println(objectMapper.writeValueAsString(message));
    out.flush();
  }

  @Override
  public void emit(McpEvent event) {
    if (event instanceof McpEvent.SessionInitialized si) {
      sessionIdSink.accept(si.sessionId());
    }
  }
}
