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
package com.callibrity.mocapi.session;

import com.callibrity.ripcurl.core.invoke.JsonRpcParamResolver;
import java.lang.reflect.Parameter;
import tools.jackson.databind.JsonNode;

/**
 * Resolves {@link McpSessionId}-annotated parameters in {@code @JsonRpc} methods. The controller
 * sets the current session ID before dispatch and clears it after.
 */
public class McpSessionIdParamResolver implements JsonRpcParamResolver {

  private final ThreadLocal<String> holder = new ThreadLocal<>();

  @Override
  public Object resolve(Parameter parameter, int index, JsonNode params) {
    if (!parameter.isAnnotationPresent(McpSessionId.class)) {
      return null;
    }
    return holder.get();
  }

  /** Sets the current session ID for the calling thread. */
  public void set(String sessionId) {
    holder.set(sessionId);
  }

  /** Clears the current session ID. Call this after dispatch completes. */
  public void clear() {
    holder.remove();
  }
}
