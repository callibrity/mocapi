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

import com.callibrity.mocapi.server.exception.McpException;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MCP protocol engine that owns validation, method dispatch, and JSON-RPC formatting. Has no
 * dependency on HTTP, SSE, or any transport layer.
 */
@Slf4j
public class McpProtocol {

  private final McpRequestValidator validator;
  private final McpMethodRegistry registry;
  private final JsonRpcMessages messages;

  public McpProtocol(
      McpRequestValidator validator, McpMethodRegistry registry, JsonRpcMessages messages) {
    this.validator = validator;
    this.registry = registry;
    this.messages = messages;
  }

  public McpRequestValidator validator() {
    return validator;
  }

  public JsonRpcMessages messages() {
    return messages;
  }

  /**
   * Processes a JSON-RPC request (that has already passed envelope and header validation). Returns
   * a protocol-level result the transport layer uses to decide the HTTP response shape.
   */
  public DispatchResult dispatch(String method, JsonNode params, JsonNode id) {
    McpMethodHandler handler = registry.lookup(method).orElse(null);
    if (handler == null) {
      log.warn("Method not found: {}", method);
      return new DispatchResult.JsonResult(
          messages.errorResponse(id, -32601, "Method not found: " + method));
    }

    return switch (handler) {
      case McpMethodHandler.Json(var fn) -> executeJson(fn, method, params, id);
      case McpMethodHandler.Streaming(var fn) -> new DispatchResult.StreamingDispatch(fn, id);
    };
  }

  private DispatchResult executeJson(
      java.util.function.Function<JsonNode, Object> fn,
      String method,
      JsonNode params,
      JsonNode id) {
    try {
      Object result = fn.apply(params);
      return new DispatchResult.JsonResult(messages.successResponse(id, result));
    } catch (McpException e) {
      log.warn("MCP error processing {}: {}", method, e.getMessage());
      return new DispatchResult.JsonResult(messages.errorResponse(id, e.getCode(), e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.warn("Invalid argument processing {}: {}", method, e.getMessage());
      return new DispatchResult.JsonResult(
          messages.errorResponse(id, -32601, "Method not found: " + method));
    } catch (RuntimeException e) {
      log.error("Error processing JSON-RPC request", e);
      return new DispatchResult.JsonResult(messages.errorResponse(id, -32603, "Internal error"));
    }
  }

  /**
   * Executes a streaming handler, returning the final JSON-RPC response (or error) as an
   * ObjectNode. The transport layer is responsible for publishing this to the stream and closing
   * it.
   */
  public ObjectNode executeStreaming(
      BiFunction<JsonNode, McpStreamContext, Object> handler,
      McpStreamContext context,
      String method,
      JsonNode params,
      JsonNode id) {
    try {
      Object result = handler.apply(params, context);
      return messages.successResponse(id, result);
    } catch (McpException e) {
      log.warn("MCP error processing {}: {}", method, e.getMessage());
      return messages.errorResponse(id, e.getCode(), e.getMessage());
    } catch (IllegalArgumentException _) {
      log.warn("Method not found: {}", method);
      return messages.errorResponse(id, -32601, "Method not found: " + method);
    } catch (RuntimeException e) {
      log.error("Error processing JSON-RPC request", e);
      return messages.errorResponse(id, -32603, "Internal error");
    }
  }

  /** Result of dispatching a method call through the protocol layer. */
  public sealed interface DispatchResult {
    /** The method was handled synchronously and produced a JSON-RPC response. */
    record JsonResult(ObjectNode response) implements DispatchResult {}

    /** The method requires streaming — the transport layer must set up a stream and invoke. */
    record StreamingDispatch(BiFunction<JsonNode, McpStreamContext, Object> handler, JsonNode id)
        implements DispatchResult {}
  }
}
