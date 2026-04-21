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
package com.callibrity.mocapi.server.guards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GuardEvaluationInterceptorTest {

  @Test
  void single_allow_guard_proceeds() {
    AtomicInteger proceeds = new AtomicInteger();
    var interceptor = new GuardEvaluationInterceptor(List.of(GuardDecision.Allow::new));

    Object result = interceptor.intercept(invocation(proceeds, "ok"));

    assertThat(result).isEqualTo("ok");
    assertThat(proceeds.get()).isEqualTo(1);
  }

  @Test
  void single_deny_guard_throws_forbidden_with_reason() {
    AtomicInteger proceeds = new AtomicInteger();
    var interceptor = new GuardEvaluationInterceptor(List.of(() -> new GuardDecision.Deny("nope")));
    var invocation = invocation(proceeds, "never");

    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("Forbidden")
        .hasMessageContaining("nope");
    assertThat(proceeds.get()).isZero();
  }

  @Test
  void empty_guard_list_proceeds() {
    AtomicInteger proceeds = new AtomicInteger();
    var interceptor = new GuardEvaluationInterceptor(List.of());

    Object result = interceptor.intercept(invocation(proceeds, "through"));

    assertThat(result).isEqualTo("through");
    assertThat(proceeds.get()).isEqualTo(1);
  }

  @Test
  void first_deny_wins_over_later_allow() {
    AtomicInteger proceeds = new AtomicInteger();
    var interceptor =
        new GuardEvaluationInterceptor(
            List.of(() -> new GuardDecision.Deny("blocked"), GuardDecision.Allow::new));
    var invocation = invocation(proceeds, "never");

    assertThatThrownBy(() -> interceptor.intercept(invocation))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("blocked");
    assertThat(proceeds.get()).isZero();
  }

  @Test
  void toString_enumerates_guards_by_description() {
    Guard first = new NamedGuard("A");
    Guard second = new NamedGuard("B");
    assertThat(new GuardEvaluationInterceptor(List.of(first, second)).toString())
        .isEqualTo(
            "Evaluates guards [A, B] and rejects denied calls with JSON-RPC -32003 Forbidden");
  }

  @Test
  void toString_with_no_guards_still_describes_role() {
    assertThat(new GuardEvaluationInterceptor(List.of()).toString())
        .isEqualTo("Evaluates guards [] and rejects denied calls with JSON-RPC -32003 Forbidden");
  }

  private record NamedGuard(String label) implements Guard {
    @Override
    public GuardDecision check() {
      return new GuardDecision.Allow();
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static MethodInvocation<Object> invocation(AtomicInteger proceeds, Object returnValue) {
    return MethodInvocation.of(
        dummyMethod(),
        new Object(),
        null,
        new Object[0],
        () -> {
          proceeds.incrementAndGet();
          return returnValue;
        });
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }
}
