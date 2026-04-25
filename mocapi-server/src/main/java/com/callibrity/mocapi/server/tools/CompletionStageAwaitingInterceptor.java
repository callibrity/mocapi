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
package com.callibrity.mocapi.server.tools;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;

/**
 * Innermost interceptor for async tool handlers. Loops over the result, calling {@code .join()} for
 * as long as it's a {@link CompletionStage}, so any depth of nested stages (including 1) is
 * unwrapped down to its terminal value in a single interceptor. Each {@link CompletionException} is
 * unwrapped so domain exceptions surface with their original type — letting outer interceptors
 * (guard short-circuits, output-schema validation, etc.) and {@link McpToolsService#invokeTool}
 * react to the real exception rather than the JDK's wrapper.
 *
 * <p>Installed by {@link CallToolHandlers#build} when the declared return type contains at least
 * one {@link CompletionStage} layer. A {@code null} return (e.g. a misbehaved async tool that
 * returned a raw {@code null} instead of a completed future) and a non-stage value both fall
 * through the loop unchanged — the downstream mapper handles whatever bubbles up.
 */
final class CompletionStageAwaitingInterceptor implements MethodInterceptor<JsonNode> {

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    Object result = invocation.proceed();
    // Peel CompletionStage layers in a loop. The classifier guarantees the declared return type
    // resolves to a non-stage after some number of unwraps; runtime values may surprise us
    // (custom CompletionStage impls returning more stages from join()), but the loop terminates
    // as soon as a non-stage value appears.
    while (result instanceof CompletionStage<?> stage) {
      try {
        result = stage.toCompletableFuture().join();
      } catch (CompletionException e) {
        throw unwrap(e);
      } catch (CancellationException e) {
        // Propagate cancellation as-is; outer service catch turns it into an error
        // CallToolResult so the client sees "tool failed" rather than an opaque 500.
        throw e;
      }
    }
    return result;
  }

  private static RuntimeException unwrap(CompletionException e) {
    return switch (e.getCause()) {
      case null -> e;
      case RuntimeException re -> re;
      case Error err -> throw err;
      // Checked exception: can't rethrow directly without declaring throws; wrap in a
      // RuntimeException that preserves the cause so McpToolsService surfaces a useful message.
      case Throwable t -> new RuntimeException(t.getMessage(), t);
    };
  }

  @Override
  public String toString() {
    return "Awaits the tool's CompletionStage return value";
  }
}
