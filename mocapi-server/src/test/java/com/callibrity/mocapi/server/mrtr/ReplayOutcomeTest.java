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

import com.callibrity.mocapi.model.ElicitRequestFormParams;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReplayOutcomeTest {

  @Test
  void fold_on_completed_invokes_only_the_completed_branch_with_the_wrapped_result() {
    ReplayOutcome<String, ElicitRequestFormParams> outcome =
        new ReplayOutcome.Completed<>("the-result");

    String tag =
        outcome.fold(
            completed -> "completed:" + completed.result(), inputRequired -> "input-required");

    assertThat(tag).isEqualTo("completed:the-result");
  }

  @Test
  void fold_on_input_required_invokes_only_the_input_required_branch_with_the_record_fields() {
    var request = new ElicitRequestFormParams("Your email?", null);
    var ledger = List.of(new ResponseLedgerEntry("elicit-1", "fingerprint-1", null));
    ReplayOutcome<String, ElicitRequestFormParams> outcome =
        new ReplayOutcome.InputRequired<>("elicit-1", request, ledger);

    String tag =
        outcome.fold(
            completed -> "completed",
            inputRequired ->
                "input-required:"
                    + inputRequired.key()
                    + ":"
                    + inputRequired.request().message()
                    + ":"
                    + inputRequired.ledger().size());

    assertThat(tag).isEqualTo("input-required:elicit-1:Your email?:1");
  }

  @Test
  void input_required_defensively_copies_the_ledger_so_the_record_is_immune_to_caller_mutation() {
    var mutableLedger =
        new ArrayList<>(List.of(new ResponseLedgerEntry("elicit-1", "fingerprint-1", null)));
    var outcome =
        new ReplayOutcome.InputRequired<String, ElicitRequestFormParams>(
            "elicit-1", new ElicitRequestFormParams("Your email?", null), mutableLedger);

    mutableLedger.add(new ResponseLedgerEntry("elicit-2", "fingerprint-2", null));

    assertThat(outcome.ledger()).hasSize(1);
  }
}
