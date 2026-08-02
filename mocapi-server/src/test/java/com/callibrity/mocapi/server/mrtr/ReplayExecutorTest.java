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
package com.callibrity.mocapi.server.mrtr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReplayExecutorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ReplayExecutor executor = new ReplayExecutor(objectMapper);

  private ElicitRequestFormParams question(String message) {
    return new ElicitRequestFormParams(message, null);
  }

  @Test
  void first_unanswered_elicit_yields_input_required_outcome_with_key_elicit_1() {
    ReplayOutcome outcome =
        executor.execute(List.of(), () -> executor.elicit(question("Your email?")));
    assertThat(outcome).isInstanceOf(ReplayOutcome.InputRequired.class);
    var ir = (ReplayOutcome.InputRequired) outcome;
    assertThat(ir.key()).isEqualTo("elicit-1");
    assertThat(ir.params().message()).isEqualTo("Your email?");
    assertThat(ir.entries()).hasSize(1);
    assertThat(ir.entries().getFirst().isAnswered()).isFalse();
  }

  @Test
  void answered_ordinal_returns_result_and_completes() {
    // Round 1: capture the ledger.
    var round1 =
        (ReplayOutcome.InputRequired)
            executor.execute(List.of(), () -> executor.elicit(question("Your email?")));
    // Answer it.
    var answer = new ElicitResult(ElicitAction.ACCEPT, JsonNodeFactory.instance.objectNode());
    List<ResponseLedgerEntry> answered = List.of(round1.entries().getFirst().answeredWith(answer));
    // Round 2: replay completes.
    ReplayOutcome outcome =
        executor.execute(answered, () -> executor.elicit(question("Your email?")).action());
    assertThat(outcome).isInstanceOf(ReplayOutcome.Completed.class);
    assertThat(((ReplayOutcome.Completed) outcome).result()).isEqualTo(ElicitAction.ACCEPT);
  }

  @Test
  void fingerprint_mismatch_at_answered_ordinal_throws_ledger_mismatch() {
    var round1 =
        (ReplayOutcome.InputRequired)
            executor.execute(List.of(), () -> executor.elicit(question("Your email?")));
    var answer = new ElicitResult(ElicitAction.ACCEPT, JsonNodeFactory.instance.objectNode());
    List<ResponseLedgerEntry> answered = List.of(round1.entries().getFirst().answeredWith(answer));
    assertThatThrownBy(
            () -> executor.execute(answered, () -> executor.elicit(question("DIFFERENT?"))))
        .isInstanceOf(ElicitationLedgerMismatchException.class);
  }

  @Test
  void elicit_outside_execute_throws_illegal_state() {
    assertThatThrownBy(() -> executor.elicit(question("hi")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MRTR dispatch");
  }
}
