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

import com.callibrity.mocapi.model.UnsupportedProtocolVersionErrorData;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.exchange.MetaEnvelopeParser;
import com.callibrity.mocapi.server.exchange.UnsupportedProtocolVersionException;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link McpServer}: per-request {@code _meta} envelope parsing followed by JSON-RPC
 * dispatch with the resulting {@link McpExchange} bound for the duration of the call (ADR-0020).
 * Holds no per-client state of any kind.
 */
public class DefaultMcpServer implements McpServer {

  private final Logger log = LoggerFactory.getLogger(DefaultMcpServer.class);
  private final JsonRpcDispatcher dispatcher;
  private final MetaEnvelopeParser envelopeParser;
  private final ObjectMapper objectMapper;

  public DefaultMcpServer(
      JsonRpcDispatcher dispatcher, MetaEnvelopeParser envelopeParser, ObjectMapper objectMapper) {
    this.dispatcher = dispatcher;
    this.envelopeParser = envelopeParser;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handleCall(JsonRpcCall call, McpTransport transport) {
    McpExchange exchange;
    try {
      exchange = envelopeParser.parse(call.params());
    } catch (JsonRpcException e) {
      log.debug("Rejecting {} call: {}", call.method(), e.getMessage());
      transport.send(call.error(e.getCode(), e.getMessage()));
      return;
    } catch (UnsupportedProtocolVersionException e) {
      log.debug("Rejecting {} call: {}", call.method(), e.getMessage());
      transport.send(unsupportedProtocolVersionError(call, e));
      return;
    }

    JsonRpcResponse response =
        ScopedValue.where(McpExchange.CURRENT, exchange)
            .where(McpTransport.CURRENT, transport)
            .call(() -> dispatcher.dispatch(call));
    if (response != null) {
      transport.send(response);
    }
  }

  @Override
  public void handleNotification(JsonRpcNotification notification) {
    dispatcher.dispatch(notification);
  }

  private JsonRpcError unsupportedProtocolVersionError(
      JsonRpcCall call, UnsupportedProtocolVersionException e) {
    return new JsonRpcError(
        new JsonRpcErrorDetail(
            UnsupportedProtocolVersionErrorData.CODE,
            e.getMessage(),
            objectMapper.valueToTree(e.data())),
        call.id());
  }
}
