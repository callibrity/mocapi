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
package com.callibrity.mocapi.http;

import tools.jackson.databind.JsonNode;

/**
 * Carries the JSON-RPC request ID through the dispatch chain via {@link ScopedValue}. Bound by the
 * controller before dispatch and read by tool invokers that need the request ID for async
 * responses.
 */
public final class McpRequestId {

  public static final ScopedValue<JsonNode> CURRENT = ScopedValue.newInstance();

  private McpRequestId() {}
}
