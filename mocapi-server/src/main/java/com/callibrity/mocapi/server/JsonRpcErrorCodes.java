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
package com.callibrity.mocapi.server;

/**
 * Mocapi-specific JSON-RPC error codes that live in JSON-RPC 2.0's implementation-defined
 * server-error range ({@code -32000} to {@code -32099}). Standard codes (parse error, invalid
 * request, method not found, invalid params, internal error) come from {@code JsonRpcProtocol}.
 *
 * <p>MCP 2026-07-28 partitions that range: {@code -32000} to {@code -32019} stays
 * implementation-defined (grandfathered SDK usage), and {@code -32020} to {@code -32099} is
 * reserved for spec-defined errors. The spec allocates {@code -32020} ({@code HeaderMismatch}),
 * {@code -32021} ({@code MissingRequiredClientCapabilityError}, data constant on {@code
 * MissingRequiredClientCapabilityErrorData}) and {@code -32022} ({@code
 * UnsupportedProtocolVersionError}, data constant on {@code UnsupportedProtocolVersionErrorData}).
 * Earlier-version codes remain reserved and are never reused: {@code -32002} (resource-not-found,
 * now {@code -32602}) and {@code -32042} (URL elicitation required, 2025-11-25 only).
 * Mocapi-private codes therefore live in the implementation-defined sub-range, at {@code -32010}
 * (ADR-0023).
 */
public final class JsonRpcErrorCodes {

  /**
   * Handler denied by a guard. A mocapi-private code in JSON-RPC's implementation-defined sub-range
   * ({@code -32000} to {@code -32019}), chosen to sit clear of the spec-reserved band {@code
   * -32020} to {@code -32099} (ADR-0023).
   */
  public static final int FORBIDDEN = -32010;

  private JsonRpcErrorCodes() {}
}
