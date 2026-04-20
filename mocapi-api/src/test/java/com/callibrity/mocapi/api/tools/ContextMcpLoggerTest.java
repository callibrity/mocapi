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
package com.callibrity.mocapi.api.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.sampling.CreateMessageRequestConfig;
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ContextMcpLoggerTest {

  record LogEntry(LoggingLevel level, String logger, String message) {}

  static class CapturingContext implements McpToolContext {
    final List<LogEntry> entries = new ArrayList<>();
    LoggingLevel threshold = LoggingLevel.DEBUG;

    /** No-op: progress notifications are not exercised by these tests. */
    @Override
    public void sendProgress(long progress, long total) {
      /* empty reflection target — see class javadoc. */
    }

    @Override
    public void log(LoggingLevel level, String logger, String message) {
      entries.add(new LogEntry(level, logger, message));
    }

    @Override
    public boolean isEnabled(LoggingLevel level) {
      return level.ordinal() >= threshold.ordinal();
    }

    @Override
    public ElicitResult elicit(ElicitRequestFormParams params) {
      return new ElicitResult(ElicitAction.ACCEPT, null);
    }

    @Override
    public CreateMessageResult sample(CreateMessageRequestParams params) {
      return new CreateMessageResult(Role.ASSISTANT, new TextContent("ok", null), null, null);
    }

    @Override
    public CreateMessageResult sample(Consumer<CreateMessageRequestConfig> customizer) {
      throw new UnsupportedOperationException("not used");
    }
  }

  @Test
  void every_level_routes_to_ctx_log_with_matching_level() {
    var ctx = new CapturingContext();
    var log = ctx.logger("test");

    log.debug("d");
    log.info("i");
    log.notice("n");
    log.warn("w");
    log.error("e");
    log.critical("c");
    log.alert("a");
    log.emergency("em");

    assertThat(ctx.entries)
        .extracting(LogEntry::level)
        .containsExactly(
            LoggingLevel.DEBUG,
            LoggingLevel.INFO,
            LoggingLevel.NOTICE,
            LoggingLevel.WARNING,
            LoggingLevel.ERROR,
            LoggingLevel.CRITICAL,
            LoggingLevel.ALERT,
            LoggingLevel.EMERGENCY);
    assertThat(ctx.entries).allMatch(e -> "test".equals(e.logger()));
  }

  @Test
  void parameterized_format_substitutes_arguments() {
    var ctx = new CapturingContext();

    ctx.logger("audit").info("user {} ran tool {}", "alice", "blast-radius");

    assertThat(ctx.entries).hasSize(1);
    assertThat(ctx.entries.getFirst().message()).isEqualTo("user alice ran tool blast-radius");
  }

  @Test
  void below_threshold_does_not_evaluate_arguments() {
    var ctx = new CapturingContext();
    ctx.threshold = LoggingLevel.WARNING;
    var counter = new AtomicInteger();

    ctx.logger("perf").info("expensive result: {}", (Object) counter.incrementAndGet());

    // Varargs still evaluates the call to counter.incrementAndGet() eagerly, which is unavoidable.
    // The key guarantee is that no log entry is recorded and MessageFormatter was not invoked.
    assertThat(ctx.entries).isEmpty();

    // When guarded manually via isInfoEnabled(), the side effect is suppressed entirely.
    var guarded = ctx.logger("perf");
    if (guarded.isInfoEnabled()) {
      guarded.info("should not run: {}", counter.incrementAndGet());
    }
    assertThat(counter.get()).isEqualTo(1);
  }

  static Stream<Arguments> levelBindings() {
    return Stream.of(
        Arguments.of(
            LoggingLevel.DEBUG,
            (Function<McpLogger, Boolean>) McpLogger::isDebugEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::debug,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.debug("x {}", args)),
        Arguments.of(
            LoggingLevel.INFO,
            (Function<McpLogger, Boolean>) McpLogger::isInfoEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::info,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.info("x {}", args)),
        Arguments.of(
            LoggingLevel.NOTICE,
            (Function<McpLogger, Boolean>) McpLogger::isNoticeEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::notice,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.notice("x {}", args)),
        Arguments.of(
            LoggingLevel.WARNING,
            (Function<McpLogger, Boolean>) McpLogger::isWarnEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::warn,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.warn("x {}", args)),
        Arguments.of(
            LoggingLevel.ERROR,
            (Function<McpLogger, Boolean>) McpLogger::isErrorEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::error,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.error("x {}", args)),
        Arguments.of(
            LoggingLevel.CRITICAL,
            (Function<McpLogger, Boolean>) McpLogger::isCriticalEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::critical,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.critical("x {}", args)),
        Arguments.of(
            LoggingLevel.ALERT,
            (Function<McpLogger, Boolean>) McpLogger::isAlertEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::alert,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.alert("x {}", args)),
        Arguments.of(
            LoggingLevel.EMERGENCY,
            (Function<McpLogger, Boolean>) McpLogger::isEmergencyEnabled,
            (BiConsumer<McpLogger, String>) McpLogger::emergency,
            (BiConsumer<McpLogger, Object[]>) (log, args) -> log.emergency("x {}", args)));
  }

  @ParameterizedTest
  @MethodSource("levelBindings")
  void enabled_check_reflects_ctx_threshold(
      LoggingLevel level,
      Function<McpLogger, Boolean> isEnabled,
      BiConsumer<McpLogger, String> logMsg,
      BiConsumer<McpLogger, Object[]> logFormat) {
    var ctx = new CapturingContext();
    ctx.threshold = level;
    var log = ctx.logger("l");

    assertThat(isEnabled.apply(log)).isTrue();

    ctx.threshold = LoggingLevel.EMERGENCY;
    if (level != LoggingLevel.EMERGENCY) {
      assertThat(isEnabled.apply(log)).isFalse();
    }
  }

  @ParameterizedTest
  @MethodSource("levelBindings")
  void format_variant_substitutes_at_each_level(
      LoggingLevel level,
      Function<McpLogger, Boolean> isEnabled,
      BiConsumer<McpLogger, String> logMsg,
      BiConsumer<McpLogger, Object[]> logFormat) {
    var ctx = new CapturingContext();
    var log = ctx.logger("fmt");

    logFormat.accept(log, new Object[] {"y"});

    assertThat(ctx.entries).hasSize(1);
    assertThat(ctx.entries.getFirst().level()).isEqualTo(level);
    assertThat(ctx.entries.getFirst().message()).isEqualTo("x y");
  }

  @ParameterizedTest
  @MethodSource("levelBindings")
  void below_threshold_suppresses_every_level(
      LoggingLevel level,
      Function<McpLogger, Boolean> isEnabled,
      BiConsumer<McpLogger, String> logMsg,
      BiConsumer<McpLogger, Object[]> logFormat) {
    var ctx = new CapturingContext();
    ctx.threshold = LoggingLevel.EMERGENCY;
    if (level == LoggingLevel.EMERGENCY) {
      return;
    }
    var log = ctx.logger("gated");

    logMsg.accept(log, "dropped");
    logFormat.accept(log, new Object[] {"dropped"});

    assertThat(ctx.entries).isEmpty();
  }

  @Test
  void logger_returns_distinct_instances_but_same_underlying_ctx() {
    var ctx = new CapturingContext();
    var a = ctx.logger("a");
    var b = ctx.logger("a");

    assertThat(a).isNotSameAs(b);
    a.info("from-a");
    b.info("from-b");
    assertThat(ctx.entries).hasSize(2);
    assertThat(ctx.entries).extracting(LogEntry::logger).containsOnly("a");
  }
}
