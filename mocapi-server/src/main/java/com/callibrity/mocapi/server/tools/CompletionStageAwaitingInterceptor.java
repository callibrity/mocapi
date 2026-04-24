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
 * Innermost interceptor for async tool handlers. When the reflective call returns a {@link
 * CompletionStage}, this interceptor blocks until the stage completes and unwraps the resulting
 * {@link CompletionException} so domain exceptions surface with their original type — letting outer
 * interceptors (guard short-circuits, output-schema validation, etc.) and {@link
 * McpToolsService#invokeTool} react to the real exception rather than the JDK's wrapper.
 *
 * <p>A {@code null} stage (e.g., a misbehaved async tool that returned a raw {@code null} instead
 * of a completed future) is passed through unchanged. Registration rejects raw and nested {@code
 * CompletionStage} declarations, so a non-{@code CompletionStage} non-null value here is a contract
 * violation and throws {@link IllegalStateException}.
 */
final class CompletionStageAwaitingInterceptor implements MethodInterceptor<JsonNode> {

  private static final String UNEXPECTED_NON_STAGE_RETURN =
      "Async tool was expected to return a CompletionStage but returned %s; this is a mocapi "
          + "invariant violation — registration should have rejected this tool if its declared "
          + "return type was not a CompletionStage.";

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    Object result = invocation.proceed();
    if (result == null) {
      return null;
    }
    if (!(result instanceof CompletionStage<?> stage)) {
      throw new IllegalStateException(
          String.format(UNEXPECTED_NON_STAGE_RETURN, result.getClass().getName()));
    }
    try {
      return stage.toCompletableFuture().join();
    } catch (CompletionException e) {
      throw unwrap(e);
    } catch (CancellationException e) {
      // Propagate cancellation as-is; the outer service catch translates it into an error
      // CallToolResult so the client sees "tool failed" rather than an opaque 500.
      throw e;
    }
  }

  private static RuntimeException unwrap(CompletionException e) {
    Throwable cause = e.getCause();
    if (cause == null) {
      return e;
    }
    if (cause instanceof RuntimeException re) {
      return re;
    }
    if (cause instanceof Error err) {
      throw err;
    }
    // Checked exception: we can't rethrow directly without declaring throws; wrap in a
    // RuntimeException that preserves the cause so McpToolsService can still surface a useful
    // error message.
    return new RuntimeException(cause.getMessage(), cause);
  }

  @Override
  public String toString() {
    return "Awaits the tool's CompletionStage return value";
  }
}
