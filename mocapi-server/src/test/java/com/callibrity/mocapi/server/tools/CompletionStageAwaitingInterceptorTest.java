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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;

/**
 * Exercises every branch of {@link CompletionStageAwaitingInterceptor} — success, failure,
 * exception-type preservation, null handling, and the invariant that the inner call must return a
 * {@link CompletionStage} (anything else is a contract violation from registration).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompletionStageAwaitingInterceptorTest {

  private final CompletionStageAwaitingInterceptor interceptor =
      new CompletionStageAwaitingInterceptor();

  @Test
  void toString_describes_role() {
    assertThat(interceptor).hasToString("Awaits the tool's CompletionStage return value");
  }

  @Nested
  class When_future_completes_successfully {

    @Test
    void already_completed_future_returns_its_value() {
      CompletionStage<String> stage = CompletableFuture.completedFuture("ready");
      Object out = interceptor.intercept(invocationReturning(stage));
      assertThat(out).isEqualTo("ready");
    }

    @Test
    void future_that_completes_on_another_thread_is_joined_and_its_value_returned() {
      // supplyAsync runs on the common fork-join pool; the interceptor's join() blocks the caller
      // until the async computation finishes — exactly the behavior we want to exercise.
      CompletableFuture<Integer> stage = CompletableFuture.supplyAsync(() -> 42);
      Object out = interceptor.intercept(invocationReturning(stage));
      assertThat(out).isEqualTo(42);
    }

    @Test
    void future_completing_with_null_produces_null() {
      CompletionStage<String> stage = CompletableFuture.completedFuture(null);
      Object out = interceptor.intercept(invocationReturning(stage));
      assertThat(out).isNull();
    }

    @Test
    void CompletableFuture_subtype_is_also_awaited() {
      // CompletableFuture implements CompletionStage; classifier allows either declaration and
      // the interceptor handles both via instanceof CompletionStage.
      CompletableFuture<String> stage = CompletableFuture.completedFuture("ready");
      Object out = interceptor.intercept(invocationReturning(stage));
      assertThat(out).isEqualTo("ready");
    }
  }

  @Nested
  class When_future_completes_exceptionally {

    @Test
    void RuntimeException_cause_surfaces_unwrapped_with_original_type() {
      var boom = new IllegalStateException("no dice");
      CompletableFuture<String> stage = CompletableFuture.failedFuture(boom);
      MethodInvocation<JsonNode> invocation = invocationReturning(stage);
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("no dice")
          .isSameAs(boom);
    }

    @Test
    void JsonRpcException_cause_preserves_code_so_outer_catch_can_distinguish_guard_denial() {
      var forbidden =
          new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "you don't have the ticket");
      CompletableFuture<String> stage = CompletableFuture.failedFuture(forbidden);
      MethodInvocation<JsonNode> invocation = invocationReturning(stage);
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS));
    }

    @Test
    void checked_exception_cause_is_wrapped_in_a_RuntimeException_preserving_message_and_cause() {
      var cause = new IOException("disk on fire");
      CompletableFuture<String> stage = CompletableFuture.failedFuture(cause);
      MethodInvocation<JsonNode> invocation = invocationReturning(stage);
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("disk on fire")
          .hasCause(cause);
    }

    @Test
    void Error_cause_is_re_thrown_unmodified() {
      var fatal = new AssertionError("world ending");
      CompletableFuture<String> stage = CompletableFuture.failedFuture(fatal);
      MethodInvocation<JsonNode> invocation = invocationReturning(stage);
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(AssertionError.class)
          .hasMessage("world ending");
    }

    @Test
    void CompletionException_with_no_cause_is_surfaced_as_itself() {
      // Degenerate case: a CompletionException constructed directly with no inner cause. We can't
      // do better than rethrowing the wrapper since there's no underlying exception to unwrap.
      var degenerate = new CompletionException("lonely wrapper", null);
      CompletableFuture<String> stage = new CompletableFuture<>();
      stage.completeExceptionally(degenerate);
      MethodInvocation<JsonNode> invocation = invocationReturning(stage);
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(CompletionException.class)
          .hasMessageContaining("lonely wrapper");
    }

    @Test
    void cancellation_propagates_as_CancellationException() {
      CompletableFuture<String> stage = new CompletableFuture<>();
      stage.cancel(true);
      MethodInvocation<JsonNode> invocation = invocationReturning(stage);
      assertThatThrownBy(() -> interceptor.intercept(invocation))
          .isInstanceOf(CancellationException.class);
    }
  }

  @Nested
  class When_invoker_returns_something_unexpected {

    @Test
    void null_stage_passes_through_without_NPE() {
      // A raw-null return from an async tool is tolerated; downstream mapper handles null.
      Object out = interceptor.intercept(invocationReturning(null));
      assertThat(out).isNull();
    }

    @Test
    void non_CompletionStage_non_null_return_falls_through_unchanged() {
      // The loop-based interceptor doesn't enforce that the return is a CompletionStage — if a
      // non-stage value bubbles up (which shouldn't happen given registration's classification,
      // but defends against runtime surprises) it just passes through and the downstream mapper
      // handles it.
      Object out = interceptor.intercept(invocationReturning("not a stage"));
      assertThat(out).isEqualTo("not a stage");
    }
  }

  // --- helpers ------------------------------------------------------------

  private MethodInvocation<JsonNode> invocationReturning(Object value) {
    Supplier<Object> continuation = () -> value;
    return MethodInvocation.of(dummyMethod(), this, null, new Object[0], continuation);
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }
}
