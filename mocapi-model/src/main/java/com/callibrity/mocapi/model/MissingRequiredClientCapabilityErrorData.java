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
package com.callibrity.mocapi.model;

/**
 * The {@code error.data} payload of the spec's {@code MissingRequiredClientCapabilityError} (code
 * {@link #CODE}): returned when processing a request requires a capability the client did not
 * declare in {@code clientCapabilities}. On HTTP the response status MUST be {@code 400 Bad
 * Request}. The JSON-RPC envelope goes through {@link JsonRpcError}.
 */
public record MissingRequiredClientCapabilityErrorData(ClientCapabilities requiredCapabilities) {

  /** The spec's {@code MISSING_REQUIRED_CLIENT_CAPABILITY} JSON-RPC error code. */
  public static final int CODE = -32021;
}
