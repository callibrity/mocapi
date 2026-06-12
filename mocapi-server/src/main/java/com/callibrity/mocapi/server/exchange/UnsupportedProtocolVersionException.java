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
package com.callibrity.mocapi.server.exchange;

import com.callibrity.mocapi.model.UnsupportedProtocolVersionErrorData;
import java.util.List;

/**
 * Thrown when a well-formed {@code _meta} envelope carries a protocol version the server does not
 * support. Dispatch maps it onto the spec's {@code UnsupportedProtocolVersionError} (JSON-RPC code
 * {@code -32004}, HTTP {@code 400 Bad Request}) whose {@code data.supported} lists the server's
 * versions — the spec's bootstrap path for clients probing an unknown server.
 *
 * <p>Distinct from a malformed/missing envelope, which is JSON-RPC {@code -32602} Invalid params.
 */
public class UnsupportedProtocolVersionException extends RuntimeException {

  private final transient UnsupportedProtocolVersionErrorData data;

  public UnsupportedProtocolVersionException(List<String> supported, String requested) {
    super("Unsupported protocol version: " + requested + " (supported: " + supported + ")");
    this.data = new UnsupportedProtocolVersionErrorData(List.copyOf(supported), requested);
  }

  /** The spec-shaped {@code error.data} payload ({@code supported} + {@code requested}). */
  public UnsupportedProtocolVersionErrorData data() {
    return data;
  }
}
