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
package com.callibrity.mocapi.server.tools;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable name→handler registry shared by {@link ToolInvocationCore} (invocation) and {@link
 * McpToolsService} (registry/pagination), built once from the same {@link CallToolHandler} list
 * (ADR-0039).
 *
 * <p>Deliberately not exposed as a bare {@code List<CallToolHandler>} Spring bean: Spring resolves
 * a collection-typed injection point by first collecting all beans of the ELEMENT type ({@code
 * CallToolHandler}), falling back to a list-typed bean only when none exist. A single {@code @Bean
 * CallToolHandler} declared anywhere else in the context would then silently replace the full
 * registered set for every consumer of the list. Wrapping the list in this named type sidesteps
 * that ambiguity — {@code CallToolHandlerRegistry} can only ever be resolved as itself.
 */
public final class CallToolHandlerRegistry {

  private final List<CallToolHandler> handlers;
  private final Map<String, CallToolHandler> byName;

  public CallToolHandlerRegistry(List<CallToolHandler> handlers) {
    this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    this.byName = this.handlers.stream().collect(Collectors.toMap(CallToolHandler::name, h -> h));
  }

  /** Every registered handler, in registration order. */
  public List<CallToolHandler> handlers() {
    return handlers;
  }

  /** Non-throwing lookup by name. */
  public Optional<CallToolHandler> findByName(String name) {
    return Optional.ofNullable(byName.get(name));
  }

  /** Looks up a handler by name, or throws the same {@code -32602} the wire path throws. */
  public CallToolHandler lookup(String name) {
    return findByName(name)
        .orElseThrow(
            () ->
                new JsonRpcException(
                    JsonRpcProtocol.INVALID_PARAMS, String.format("Tool %s not found.", name)));
  }
}
