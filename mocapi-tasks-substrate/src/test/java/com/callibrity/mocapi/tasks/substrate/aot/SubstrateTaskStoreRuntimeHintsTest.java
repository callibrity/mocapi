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
package com.callibrity.mocapi.tasks.substrate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class SubstrateTaskStoreRuntimeHintsTest {

  @Test
  void registersBindingHintsForTheSerializedRecordGraph() {
    RuntimeHints hints = new RuntimeHints();
    new SubstrateTaskStoreRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(RuntimeHintsPredicates.reflection().onType(TaskRecord.class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(ResponseLedgerEntry.class))
        .accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(JsonRpcErrorDetail.class)).accepts(hints);
  }
}
