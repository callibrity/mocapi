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

/**
 * Mocapi-specific JSON-RPC error codes that live in JSON-RPC 2.0's implementation-defined
 * server-error range ({@code -32000} to {@code -32099}). Standard codes (parse error, invalid
 * request, method not found, invalid params, internal error) come from {@code JsonRpcProtocol}.
 *
 * <p>MCP 2026-07-28 claims part of the same range for spec-defined errors: {@code -32003} ({@code
 * MissingRequiredClientCapabilityError}, data constant on {@code
 * MissingRequiredClientCapabilityErrorData}) and {@code -32004} ({@code
 * UnsupportedProtocolVersionError}, data constant on {@code UnsupportedProtocolVersionErrorData}),
 * with {@code -32001} reserved by the Streamable HTTP transport prose ({@code HeaderMismatch}) and
 * {@code -32002} historically meaning resource-not-found (now {@code -32602} in this revision).
 * Mocapi-private codes therefore start at {@code -32010} (ADR-0023).
 */
public final class JsonRpcErrorCodes {

  /**
   * Handler denied by a guard. Moved off {@code -32003} when MCP 2026-07-28 assigned that code to
   * {@code MissingRequiredClientCapabilityError} (ADR-0023).
   */
  public static final int FORBIDDEN = -32010;

  private JsonRpcErrorCodes() {}
}
