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

import com.callibrity.mocapi.model.CancelledNotificationParams;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.time.Duration;
import java.util.UUID;
import org.jwcarman.substrate.NextResult;
import org.jwcarman.substrate.mailbox.Mailbox;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Correlates outbound JSON-RPC requests with inbound client responses using Substrate's {@link
 * Mailbox} primitive. Each outbound request creates a mailbox keyed by a UUID correlation ID. The
 * caller blocks on the mailbox until the client posts back or a timeout elapses.
 */
public class McpResponseCorrelationService {

  private static final Duration DEFAULT_MAILBOX_TTL = Duration.ofMinutes(5);

  private final MailboxFactory mailboxFactory;
  private final ObjectMapper objectMapper;
  private final Duration timeout;

  public McpResponseCorrelationService(
      MailboxFactory mailboxFactory, ObjectMapper objectMapper, Duration timeout) {
    this.mailboxFactory = mailboxFactory;
    this.objectMapper = objectMapper;
    this.timeout = timeout;
  }

  /**
   * Sends a JSON-RPC call to the client via transport and blocks waiting for the correlated
   * response.
   *
   * @param <T> the expected result type
   * @param method the JSON-RPC method name (e.g., {@link McpMethods#ELICITATION_CREATE})
   * @param params the request parameters
   * @param resultType the class to deserialize the response result into
   * @param transport the transport to send the request through
   * @return the deserialized response
   * @throws McpClientResponseTimeoutException if the client does not respond within the configured
   *     timeout
   */
  public <T> T sendAndAwait(
      String method, Object params, Class<T> resultType, McpTransport transport) {
    String correlationId = UUID.randomUUID().toString();
    Mailbox<JsonNode> mailbox =
        mailboxFactory.create(
            "mcp:correlation:" + correlationId, JsonNode.class, DEFAULT_MAILBOX_TTL);

    try (var subscription = mailbox.subscribe()) {
      JsonNode paramsNode = objectMapper.valueToTree(params);
      JsonNode idNode = JsonNodeFactory.instance.stringNode(correlationId);
      JsonRpcCall call = new JsonRpcCall("2.0", method, paramsNode, idNode);
      transport.send(call);

      NextResult<JsonNode> next = subscription.next(timeout);
      return switch (next) {
        case NextResult.Value<JsonNode>(var value) -> objectMapper.treeToValue(value, resultType);
        case NextResult.Timeout<JsonNode> _ -> {
          sendCancelled(transport, correlationId, "Server timeout waiting for client response");
          throw new McpClientResponseTimeoutException(
              "Timed out waiting for client response to " + method + " (id=" + correlationId + ")");
        }
        default ->
            throw new McpClientResponseTimeoutException(
                "Mailbox terminated before client response to "
                    + method
                    + " (id="
                    + correlationId
                    + ")");
      };
    } finally {
      mailbox.delete();
    }
  }

  private void sendCancelled(McpTransport transport, String requestId, String reason) {
    var params = new CancelledNotificationParams(requestId, reason, null);
    transport.send(
        new JsonRpcNotification(
            "2.0", McpMethods.NOTIFICATIONS_CANCELLED, objectMapper.valueToTree(params)));
  }

  /**
   * Delivers a client response to the waiting mailbox identified by the response's ID field.
   * Silently drops the response if no mailbox is waiting (orphan response).
   *
   * @param response the client's JSON-RPC response
   */
  public void deliver(JsonRpcResponse response) {
    String correlationId = response.id().asString();
    Mailbox<JsonNode> mailbox =
        mailboxFactory.connect("mcp:correlation:" + correlationId, JsonNode.class);
    try {
      if (response instanceof JsonRpcResult result) {
        mailbox.deliver(result.result());
      }
    } catch (Exception _) {
      // Orphan response — no waiting mailbox or mailbox already expired/delivered.
    }
  }
}
