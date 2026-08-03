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
package com.callibrity.mocapi.tasks.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.InputRequest;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Covers the terminal-status guard shared by every {@link TaskRecord} transition helper: a no-op
 * ({@code this} unchanged) once {@link TaskStatus#isTerminal()}, otherwise a rebuilt record with
 * {@code version} bumped and {@code lastUpdatedAt} advanced.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskRecordTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-02T00:00:00Z");
  private static final Instant LATER = Instant.parse("2026-08-02T00:05:00Z");

  private TaskRecord newRecord(TaskStatus status) {
    return new TaskRecord(
        "t1",
        "demo.tool",
        null,
        "user-1",
        "2026-07-28",
        null,
        status,
        "0",
        CREATED_AT,
        CREATED_AT,
        Duration.ofHours(1),
        Duration.ofSeconds(1),
        List.of(),
        Map.of(),
        null,
        null,
        0L);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void working_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);

    assertThat(record.working(LATER)).isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void completed_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);

    assertThat(record.completed(new CallToolResult(List.of(), false, null, "complete"), LATER))
        .isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void failed_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);

    assertThat(record.failed(new JsonRpcErrorDetail(-32000, "boom"), "boom", LATER))
        .isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void cancelled_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);

    assertThat(record.cancelled(LATER)).isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void inputRequired_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);
    InputRequest request = new ElicitRequest(new ElicitRequestFormParams("please answer", null));

    assertThat(record.inputRequired("elicit-1", request, List.of(), LATER)).isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void withStatusMessage_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);

    assertThat(record.withStatusMessage("halfway", LATER)).isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"COMPLETED", "FAILED", "CANCELLED"})
  void withLedger_is_a_no_op_once_terminal(TaskStatus terminal) {
    TaskRecord record = newRecord(terminal);

    assertThat(record.withLedger(List.of(new ResponseLedgerEntry("elicit-1", "fp-1", null)), LATER))
        .isSameAs(record);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void working_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);

    TaskRecord result = record.working(LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(TaskStatus.WORKING);
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void completed_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);
    CallToolResult toolResult = new CallToolResult(List.of(), false, null, "complete");

    TaskRecord result = record.completed(toolResult, LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(result.result()).isEqualTo(toolResult);
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void failed_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);
    JsonRpcErrorDetail error = new JsonRpcErrorDetail(-32000, "boom");

    TaskRecord result = record.failed(error, "boom", LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
    assertThat(result.error()).isEqualTo(error);
    assertThat(result.statusMessage()).isEqualTo("boom");
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void cancelled_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);

    TaskRecord result = record.cancelled(LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(TaskStatus.CANCELLED);
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void inputRequired_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);
    InputRequest request = new ElicitRequest(new ElicitRequestFormParams("please answer", null));
    List<ResponseLedgerEntry> ledger = List.of(new ResponseLedgerEntry("elicit-1", "fp-1", null));

    TaskRecord result = record.inputRequired("elicit-1", request, ledger, LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
    assertThat(result.inputRequests()).isEqualTo(Map.of("elicit-1", request));
    assertThat(result.ledger()).isEqualTo(ledger);
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void withStatusMessage_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);

    TaskRecord result = record.withStatusMessage("halfway", LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(nonTerminal);
    assertThat(result.statusMessage()).isEqualTo("halfway");
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"WORKING", "INPUT_REQUIRED"})
  void withLedger_advances_a_non_terminal_record(TaskStatus nonTerminal) {
    TaskRecord record = newRecord(nonTerminal);
    List<ResponseLedgerEntry> ledger = List.of(new ResponseLedgerEntry("elicit-1", "fp-1", null));

    TaskRecord result = record.withLedger(ledger, LATER);

    assertThat(result).isNotSameAs(record);
    assertThat(result.status()).isEqualTo(nonTerminal);
    assertThat(result.ledger()).isEqualTo(ledger);
    assertThat(result.lastUpdatedAt()).isEqualTo(LATER);
    assertThat(result.version()).isEqualTo(record.version() + 1);
  }
}
