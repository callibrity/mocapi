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

import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Jackson binding hints for the record graph {@code SubstrateTaskStore} serializes
 * through codec-jackson: {@link TaskRecord} plus the referenced types that live outside packages
 * already hinted elsewhere. {@code mocapi-server}'s {@code MocapiRuntimeHints} covers {@code
 * com.callibrity.mocapi.model} (content blocks, elicitation types) and {@code mocapi-tasks}' {@code
 * TasksRuntimeHints} covers the tasks wire model — but {@link TaskRecord} (store package), {@link
 * ResponseLedgerEntry} (MRTR ledger), and ripcurl's {@link JsonRpcErrorDetail} are only reachable
 * via this module's serialization, so this module owns their hints (each extension registers hints
 * for what it alone makes reachable — the pattern ADR-0037 established).
 *
 * <p>{@link BindingReflectionHintsRegistrar} walks nested property types transitively, so
 * registering these roots also covers everything they reference.
 */
public class SubstrateTaskStoreRuntimeHints implements RuntimeHintsRegistrar {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(
        hints.reflection(), TaskRecord.class, ResponseLedgerEntry.class, JsonRpcErrorDetail.class);
  }
}
