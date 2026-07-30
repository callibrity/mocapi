/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.transport.http;

import com.callibrity.mocapi.model.MissingRequiredClientCapabilityErrorData;
import com.callibrity.mocapi.model.UnsupportedProtocolVersionErrorData;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.springframework.http.HttpStatus;

/**
 * The single JSON-RPC-error-code-to-HTTP-status mapping table for the Streamable HTTP transport
 * (MCP 2026-07-28).
 *
 * <ul>
 *   <li>{@code -32601} Method not found → {@code 404 Not Found} — distinguishes a modern server's
 *       unknown-RPC-method from a legacy HTTP+SSE endpoint 404.
 *   <li>{@code -32700}, {@code -32600}, {@code -32602}, {@code -32020} ({@code HeaderMismatch}),
 *       {@code -32021} ({@code MissingRequiredClientCapabilityError}), {@code -32022} ({@code
 *       UnsupportedProtocolVersionError}) → {@code 400 Bad Request}.
 *   <li>Everything else (internal errors, application-level errors) → {@code 200 OK}: the error is
 *       a well-formed JSON-RPC response, so the HTTP exchange itself succeeded.
 * </ul>
 *
 * <p>The mapping applies only when the response is delivered as direct JSON. Once the response
 * stream has committed as SSE, the HTTP status is already {@code 200} and errors travel on the
 * stream.
 */
public final class HttpStatusMapping {

  private HttpStatusMapping() {}

  /** Maps a JSON-RPC response to the HTTP status of a direct (non-SSE) reply. */
  public static HttpStatus forResponse(JsonRpcResponse response) {
    return switch (response) {
      case JsonRpcResult _ -> HttpStatus.OK;
      case JsonRpcError error -> forErrorCode(error.error().code());
    };
  }

  /** Maps a JSON-RPC error code to the HTTP status of a direct (non-SSE) reply. */
  public static HttpStatus forErrorCode(int code) {
    return switch (code) {
      case JsonRpcProtocol.PARSE_ERROR,
          JsonRpcProtocol.INVALID_REQUEST,
          JsonRpcProtocol.INVALID_PARAMS,
          McpHeaderValidator.HEADER_MISMATCH_CODE,
          MissingRequiredClientCapabilityErrorData.CODE,
          UnsupportedProtocolVersionErrorData.CODE ->
          HttpStatus.BAD_REQUEST;
      case JsonRpcProtocol.METHOD_NOT_FOUND -> HttpStatus.NOT_FOUND;
      default -> HttpStatus.OK;
    };
  }
}
