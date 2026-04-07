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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Utility for building JSON-RPC 2.0 response and error messages. */
public final class JsonRpcMessages {

  public static final String JSONRPC_VERSION = "2.0";
  public static final String JSONRPC_FIELD = "jsonrpc";

  private final ObjectMapper objectMapper;

  public JsonRpcMessages(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Builds a JSON-RPC 2.0 success response. */
  public ObjectNode successResponse(JsonNode id, Object result) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put(JSONRPC_FIELD, JSONRPC_VERSION);
    if (id != null) {
      response.set("id", id);
    }
    if (result != null) {
      response.set("result", objectMapper.valueToTree(result));
    }
    return response;
  }

  /** Builds a JSON-RPC 2.0 error response. */
  public ObjectNode errorResponse(JsonNode id, int code, String message) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put(JSONRPC_FIELD, JSONRPC_VERSION);
    if (id != null) {
      response.set("id", id);
    }

    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    response.set("error", error);

    return response;
  }
}
