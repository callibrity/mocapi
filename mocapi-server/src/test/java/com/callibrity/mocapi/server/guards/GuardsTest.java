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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GuardsTest {

  @Test
  void empty_list_allows() {
    assertThat(Guards.evaluate(List.of())).isInstanceOf(GuardDecision.Allow.class);
    assertThat(Guards.allows(List.of())).isTrue();
  }

  @Test
  void all_allow_returns_allow() {
    Guard a = GuardDecision.Allow::new;
    Guard b = GuardDecision.Allow::new;
    assertThat(Guards.evaluate(List.of(a, b))).isInstanceOf(GuardDecision.Allow.class);
  }

  @Test
  void first_deny_short_circuits_subsequent_guards() {
    AtomicInteger secondInvocations = new AtomicInteger();
    Guard first = () -> new GuardDecision.Deny("nope");
    Guard second =
        () -> {
          secondInvocations.incrementAndGet();
          return new GuardDecision.Allow();
        };
    GuardDecision decision = Guards.evaluate(List.of(first, second));
    assertThat(decision).isEqualTo(new GuardDecision.Deny("nope"));
    assertThat(secondInvocations).hasValue(0);
  }

  @Test
  void deny_after_allow_returns_that_deny() {
    Guard allow = GuardDecision.Allow::new;
    Guard deny = () -> new GuardDecision.Deny("rejected");
    GuardDecision decision = Guards.evaluate(List.of(allow, deny));
    assertThat(decision).isEqualTo(new GuardDecision.Deny("rejected"));
    assertThat(Guards.allows(List.of(allow, deny))).isFalse();
  }
}
