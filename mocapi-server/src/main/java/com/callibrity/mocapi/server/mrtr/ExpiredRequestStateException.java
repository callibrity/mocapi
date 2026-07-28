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
package com.callibrity.mocapi.server.mrtr;

/**
 * Thrown by {@link RequestStateCodec#decode(String)} when a {@code requestState} token
 * authenticates correctly but its {@code issuedAt} is older than the configured TTL ({@code
 * mocapi.mrtr.ttl}). The elicitation conversation has lapsed; the client must start the original
 * request over.
 */
public class ExpiredRequestStateException extends InvalidRequestStateException {

  public ExpiredRequestStateException(String message) {
    super(message);
  }
}
