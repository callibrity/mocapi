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

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Reads newline-delimited JSON-RPC messages from stdin (or an injected reader), dispatches each on
 * its own virtual thread through {@link McpServer}, and returns when the input closes.
 *
 * <p>MCP 2026-07-28 is stateless (ADR-0019, ADR-0020): there is no handshake and no per-client
 * state, so every line is an independent request or notification. {@code server/discover} — the
 * back-compat probe — is answerable at any time, including as the very first message. All envelope
 * semantics ({@code _meta} parsing, {@code -32602}/{@code -32022}) live in the server core; this
 * transport only frames messages and relays responses.
 *
 * <p>Each message runs on its own virtual thread so a slow handler doesn't stall the reader thread.
 */
public final class StdioServer {

  private final Logger log = LoggerFactory.getLogger(StdioServer.class);

  private final McpServer server;
  private final ObjectMapper objectMapper;
  private final StdioTransport transport;
  private final BufferedReader input;

  public StdioServer(
      McpServer server, ObjectMapper objectMapper, StdioTransport transport, BufferedReader input) {
    this.server = server;
    this.objectMapper = objectMapper;
    this.transport = transport;
    this.input = input;
  }

  public static BufferedReader stdin() {
    return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
  }

  /**
   * Runs the reader loop until input closes. Each inbound message is dispatched on a virtual
   * thread; on EOF the executor closes and awaits in-flight dispatches so the JVM doesn't exit
   * before their stdout responses are written. {@link java.util.concurrent.ExecutorService#close}
   * performs shutdown + awaitTermination in one shot.
   */
  public void run() throws IOException {
    try (var dispatcher =
            Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("mocapi-stdio-dispatch-", 0).factory());
        BufferedReader reader = input) {
      String line;
      while ((line = reader.readLine()) != null) {
        final String captured = line;
        dispatcher.submit(() -> dispatch(captured));
      }
    }
    log.info("stdin closed; stdio server exiting");
  }

  private void dispatch(String line) {
    JsonRpcMessage message;
    try {
      message = objectMapper.readValue(line, JsonRpcMessage.class);
    } catch (Exception e) {
      log.warn("Dropped malformed JSON-RPC message: {}", e.getMessage());
      return;
    }

    switch (message) {
      case JsonRpcCall call -> handleCall(call);
      case JsonRpcNotification notification -> handleNotification(notification);
      case JsonRpcResponse response -> handleResponse(response);
    }
  }

  private void handleCall(JsonRpcCall call) {
    try {
      server.handleCall(call, transport);
    } catch (Exception e) {
      log.error("Handler threw during call {}", call.method(), e);
      sendError(call.id(), JsonRpcProtocol.INTERNAL_ERROR, "Internal error: " + e.getMessage());
    }
  }

  private void handleNotification(JsonRpcNotification notification) {
    try {
      server.handleNotification(notification);
    } catch (Exception e) {
      log.error("Notification handler threw for {}", notification.method(), e);
    }
  }

  private void handleResponse(JsonRpcResponse response) {
    // MCP 2026-07-28 has no server-initiated requests (ADR-0020), so a client has nothing to
    // respond to. Drop with a log rather than answering — responses get no responses.
    log.warn(
        "Dropped unexpected client response (id {}): no server-initiated requests in MCP {}",
        response.id(),
        McpServer.PROTOCOL_VERSION);
  }

  private void sendError(JsonNode id, int code, String message) {
    JsonNode errorId = id == null ? JsonNodeFactory.instance.nullNode() : id;
    transport.send(
        new JsonRpcError(JsonRpcProtocol.VERSION, new JsonRpcErrorDetail(code, message), errorId));
  }
}
