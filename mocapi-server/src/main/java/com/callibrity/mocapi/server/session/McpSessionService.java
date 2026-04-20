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
package com.callibrity.mocapi.server.session;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeRequestParams;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Orchestrates session lifecycle: create, find, delete, log-level updates, and initialize. */
public class McpSessionService {

  private final Logger log = LoggerFactory.getLogger(McpSessionService.class);
  private final McpSessionStore store;
  private final Duration ttl;
  private final Implementation serverInfo;
  private final String instructions;
  private final ServerCapabilities capabilities;

  public McpSessionService(
      McpSessionStore store,
      Duration ttl,
      Implementation serverInfo,
      String instructions,
      ServerCapabilities capabilities) {
    this.store = store;
    this.ttl = ttl;
    this.serverInfo = serverInfo;
    this.instructions = instructions;
    this.capabilities = capabilities;
  }

  @JsonRpcMethod(McpMethods.INITIALIZE)
  public InitializeResult initialize(@JsonRpcParams InitializeRequestParams params) {
    String sessionId = UUID.randomUUID().toString();
    McpSession session =
        new McpSession(
            sessionId, params.protocolVersion(), params.capabilities(), params.clientInfo());
    create(session);
    McpTransport transport = McpTransport.CURRENT.get();
    transport.emit(new McpEvent.SessionInitialized(sessionId, params.protocolVersion()));
    return new InitializeResult(McpServer.PROTOCOL_VERSION, capabilities, serverInfo, instructions);
  }

  /** Saves the session to the store and returns the session ID. */
  public String create(McpSession session) {
    store.save(session, ttl);
    return session.sessionId();
  }

  /** Looks up a session by ID, extending TTL on hit. */
  public Optional<McpSession> find(String sessionId) {
    Optional<McpSession> result = store.find(sessionId);
    result.ifPresent(_ -> store.touch(sessionId, ttl));
    return result;
  }

  /** Removes a session from the store. */
  public void delete(String sessionId) {
    store.delete(sessionId);
  }

  /** Updates the log level for the given session. */
  public void setLogLevel(String sessionId, LoggingLevel level) {
    store
        .find(sessionId)
        .ifPresent(session -> store.update(sessionId, session.withLogLevel(level)));
  }

  /** Marks the given session as initialized. No-op if the session is not found. */
  public void markInitialized(String sessionId) {
    log.debug("markInitialized: looking up session {}", sessionId);
    store
        .find(sessionId)
        .ifPresentOrElse(
            session -> {
              log.debug("markInitialized: updating session {} to initialized=true", sessionId);
              store.update(sessionId, session.withInitialized(true));
              log.debug("markInitialized: update complete for session {}", sessionId);
            },
            () -> log.warn("markInitialized: session {} not found", sessionId));
  }
}
