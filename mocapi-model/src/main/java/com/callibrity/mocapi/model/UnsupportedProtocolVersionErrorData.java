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
package com.callibrity.mocapi.model;

import java.util.List;

/**
 * The {@code error.data} payload of the spec's {@code UnsupportedProtocolVersionError} (code {@link
 * #CODE}): returned when the request's protocol version is unknown to or unsupported by the server.
 * On HTTP the response status MUST be {@code 400 Bad Request}. The JSON-RPC envelope goes through
 * {@link JsonRpcError}.
 */
public record UnsupportedProtocolVersionErrorData(List<String> supported, String requested) {

  /** The spec's {@code UNSUPPORTED_PROTOCOL_VERSION} JSON-RPC error code. */
  public static final int CODE = -32022;
}
