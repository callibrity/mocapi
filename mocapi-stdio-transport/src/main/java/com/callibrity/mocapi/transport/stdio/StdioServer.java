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

import static com.callibrity.mocapi.model.McpMethods.INITIALIZE;
import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult;
import com.callibrity.mocapi.server.McpContextResult.ProtocolVersionMismatch;
import com.callibrity.mocapi.server.McpContextResult.SessionIdRequired;
import com.callibrity.mocapi.server.McpContextResult.SessionNotFound;
import com.callibrity.mocapi.server.McpContextResult.ValidContext;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Reads newline-delimited JSON-RPC messages from stdin (or an injected {@link Reader}), dispatches
 * each on its own virtual thread through {@link McpServer}, and returns when the input closes.
 *
 * <p>Each message runs on its own virtual thread so handlers that block awaiting a client response
 * (elicitation, sampling) don't deadlock the reader thread.
 */
public final class StdioServer {

  private final Logger log = LoggerFactory.getLogger(StdioServer.class);

  private final McpServer server;
  private final ObjectMapper objectMapper;
  private final StdioTransport transport;
  private final BufferedReader input;
  private final Supplier<String> sessionIdSource;

  public StdioServer(
      McpServer server,
      ObjectMapper objectMapper,
      StdioTransport transport,
      BufferedReader input,
      Supplier<String> sessionIdSource) {
    this.server = server;
    this.objectMapper = objectMapper;
    this.transport = transport;
    this.input = input;
    this.sessionIdSource = sessionIdSource;
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
    McpContext context;
    if (INITIALIZE.equals(call.method())) {
      context = McpContext.empty();
    } else {
      McpContextResult result = server.createContext(sessionIdSource.get(), null);
      switch (result) {
        case ValidContext(var ctx) -> context = ctx;
        case SessionIdRequired(var code, var msg) -> {
          sendError(call.id(), code, msg);
          return;
        }
        case SessionNotFound(var code, var msg) -> {
          sendError(call.id(), code, msg);
          return;
        }
        case ProtocolVersionMismatch(var code, var msg) -> {
          sendError(call.id(), code, msg);
          return;
        }
      }
    }
    try {
      server.handleCall(context, call, transport);
    } catch (Exception e) {
      log.error("Handler threw during call {}", call.method(), e);
      sendError(call.id(), -32603, "Internal error: " + e.getMessage());
    }
  }

  private void handleNotification(JsonRpcNotification notification) {
    String sid = sessionIdSource.get();
    if (sid == null) {
      log.debug("Dropped pre-initialize notification {}: no session yet", notification.method());
      return;
    }
    if (server.createContext(sid, null) instanceof ValidContext(var ctx)) {
      server.handleNotification(ctx, notification);
    }
  }

  private void handleResponse(JsonRpcResponse response) {
    String sid = sessionIdSource.get();
    if (sid == null) {
      log.debug("Dropped pre-initialize client response: no session yet");
      return;
    }
    if (server.createContext(sid, null) instanceof ValidContext(var ctx)) {
      server.handleResponse(ctx, response);
    }
  }

  private void sendError(JsonNode id, int code, String message) {
    JsonNode errorId = id == null ? JsonNodeFactory.instance.nullNode() : id;
    transport.send(new JsonRpcError(VERSION, new JsonRpcErrorDetail(code, message), errorId));
  }
}
