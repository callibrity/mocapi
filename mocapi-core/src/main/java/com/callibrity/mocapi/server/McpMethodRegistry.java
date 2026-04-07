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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for MCP method handlers. Maps method names to their {@link McpMethodHandler}
 * implementations.
 */
public class McpMethodRegistry {

  private final Map<String, McpMethodHandler> handlers;

  private McpMethodRegistry(Map<String, McpMethodHandler> handlers) {
    this.handlers = Map.copyOf(handlers);
  }

  /** Looks up a handler by method name. */
  public Optional<McpMethodHandler> lookup(String method) {
    return Optional.ofNullable(handlers.get(method));
  }

  /** Creates a new builder for constructing a registry. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for constructing an {@link McpMethodRegistry}. */
  public static final class Builder {
    private final Map<String, McpMethodHandler> handlers = new LinkedHashMap<>();

    private Builder() {}

    public Builder register(String method, McpMethodHandler handler) {
      handlers.put(method, handler);
      return this;
    }

    public McpMethodRegistry build() {
      return new McpMethodRegistry(handlers);
    }
  }
}
