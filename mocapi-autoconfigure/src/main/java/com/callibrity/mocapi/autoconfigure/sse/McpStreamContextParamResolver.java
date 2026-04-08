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
package com.callibrity.mocapi.autoconfigure.sse;

import com.callibrity.mocapi.server.McpStreamContext;
import com.callibrity.ripcurl.core.invoke.JsonRpcParamResolver;
import java.lang.reflect.Parameter;
import tools.jackson.databind.JsonNode;

/**
 * Resolves {@link McpStreamContext} parameters in {@code @JsonRpc} methods. The controller sets the
 * current context before dispatch and clears it after.
 */
public class McpStreamContextParamResolver implements JsonRpcParamResolver {

  private final ThreadLocal<McpStreamContext> holder = new ThreadLocal<>();
  private final ThreadLocal<Boolean> resolved = ThreadLocal.withInitial(() -> Boolean.FALSE);

  @Override
  public Object resolve(Parameter parameter, int index, JsonNode params) {
    if (!McpStreamContext.class.isAssignableFrom(parameter.getType())) {
      return null;
    }
    resolved.set(Boolean.TRUE);
    return holder.get();
  }

  /** Sets the current stream context for the calling thread. */
  public void set(McpStreamContext context) {
    holder.set(context);
    resolved.set(Boolean.FALSE);
  }

  /** Clears the current stream context. Call this after dispatch completes. */
  public void clear() {
    holder.remove();
    resolved.remove();
  }

  /**
   * Returns true if an {@link McpStreamContext} parameter was resolved during the last dispatch.
   */
  public boolean wasResolved() {
    return Boolean.TRUE.equals(resolved.get());
  }
}
