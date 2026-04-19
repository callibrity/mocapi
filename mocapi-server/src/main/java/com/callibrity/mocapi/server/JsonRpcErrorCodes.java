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
 */
public final class JsonRpcErrorCodes {

  /** Handler denied by a guard. */
  public static final int FORBIDDEN = -32003;

  private JsonRpcErrorCodes() {}
}
