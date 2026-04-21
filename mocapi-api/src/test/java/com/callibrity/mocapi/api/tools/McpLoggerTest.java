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

import com.callibrity.mocapi.model.LoggingLevel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the default per-level shortcuts on the {@link McpLogger} interface. Every {@code
 * isXxxEnabled()} method delegates to {@link McpLogger#isEnabled(LoggingLevel)}; every {@code
 * xxx(String)} / {@code xxx(String, Object...)} pair delegates to {@link
 * McpLogger#log(LoggingLevel, String)} with the matching level. A tiny {@link RecordingLogger}
 * captures the two abstract calls so each shortcut can be asserted as an independent mapping.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpLoggerTest {

  /** Captures every call to the two abstract methods for later assertion. */
  private static final class RecordingLogger implements McpLogger {
    record LogEntry(LoggingLevel level, String message) {}

    final Set<LoggingLevel> enabledLevels = EnumSet.allOf(LoggingLevel.class);
    final List<LogEntry> entries = new ArrayList<>();

    @Override
    public boolean isEnabled(LoggingLevel level) {
      return enabledLevels.contains(level);
    }

    @Override
    public void log(LoggingLevel level, String message) {
      entries.add(new LogEntry(level, message));
    }
  }

  @Nested
  class Level_predicates_delegate_to_is_enabled {

    @Test
    void every_is_xxx_enabled_returns_true_when_that_level_is_enabled() {
      for (LoggingLevel level : LoggingLevel.values()) {
        var logger = new RecordingLogger();
        logger.enabledLevels.clear();
        logger.enabledLevels.add(level);
        assertThat(predicateFor(logger, level)).as("only %s should be enabled", level).isTrue();
      }
    }

    @Test
    void every_is_xxx_enabled_returns_false_when_that_level_is_disabled() {
      for (LoggingLevel level : LoggingLevel.values()) {
        var logger = new RecordingLogger();
        logger.enabledLevels.remove(level);
        assertThat(predicateFor(logger, level)).as("%s should be disabled", level).isFalse();
      }
    }

    private boolean predicateFor(McpLogger logger, LoggingLevel level) {
      return switch (level) {
        case DEBUG -> logger.isDebugEnabled();
        case INFO -> logger.isInfoEnabled();
        case NOTICE -> logger.isNoticeEnabled();
        case WARNING -> logger.isWarnEnabled();
        case ERROR -> logger.isErrorEnabled();
        case CRITICAL -> logger.isCriticalEnabled();
        case ALERT -> logger.isAlertEnabled();
        case EMERGENCY -> logger.isEmergencyEnabled();
      };
    }
  }

  @Nested
  class Simple_string_overloads_route_to_log_with_matching_level {

    @Test
    void every_simple_string_shortcut_forwards_at_the_matching_level() {
      for (LoggingLevel level : LoggingLevel.values()) {
        var logger = new RecordingLogger();
        simpleOverloadFor(level).accept(logger, "msg-" + level);
        assertThat(logger.entries)
            .singleElement()
            .isEqualTo(new RecordingLogger.LogEntry(level, "msg-" + level));
      }
    }

    private BiConsumer<McpLogger, String> simpleOverloadFor(LoggingLevel level) {
      return switch (level) {
        case DEBUG -> McpLogger::debug;
        case INFO -> McpLogger::info;
        case NOTICE -> McpLogger::notice;
        case WARNING -> McpLogger::warn;
        case ERROR -> McpLogger::error;
        case CRITICAL -> McpLogger::critical;
        case ALERT -> McpLogger::alert;
        case EMERGENCY -> McpLogger::emergency;
      };
    }
  }

  @Nested
  class Parameterized_overloads_format_and_respect_is_enabled {

    @Test
    void every_parameterized_shortcut_formats_placeholders_when_enabled() {
      for (LoggingLevel level : LoggingLevel.values()) {
        var logger = new RecordingLogger();
        formattedOverloadFor(level).accept(logger, "x={} y={}");
        assertThat(logger.entries)
            .singleElement()
            .isEqualTo(new RecordingLogger.LogEntry(level, "x=1 y=2"));
      }
    }

    @Test
    void every_parameterized_shortcut_skips_log_when_level_disabled() {
      for (LoggingLevel level : LoggingLevel.values()) {
        var logger = new RecordingLogger();
        logger.enabledLevels.remove(level);
        formattedOverloadFor(level).accept(logger, "ignored {}");
        assertThat(logger.entries).as("%s should have produced no log entry", level).isEmpty();
      }
    }

    private BiConsumer<McpLogger, String> formattedOverloadFor(LoggingLevel level) {
      return (logger, fmt) -> invokeFormatted(logger, level, fmt, 1, 2);
    }

    private void invokeFormatted(McpLogger logger, LoggingLevel level, String fmt, Object... args) {
      switch (level) {
        case DEBUG -> logger.debug(fmt, args);
        case INFO -> logger.info(fmt, args);
        case NOTICE -> logger.notice(fmt, args);
        case WARNING -> logger.warn(fmt, args);
        case ERROR -> logger.error(fmt, args);
        case CRITICAL -> logger.critical(fmt, args);
        case ALERT -> logger.alert(fmt, args);
        case EMERGENCY -> logger.emergency(fmt, args);
      }
    }
  }

  @Nested
  class Simple_string_overloads_bypass_is_enabled {

    @Test
    void simple_overloads_call_log_even_when_level_is_disabled() {
      // The no-format overloads delegate straight to log(); it's the impl's job to gate.
      // This documents that contract for the default methods.
      Function<McpLogger, Boolean> invokes =
          logger -> {
            Consumer<String> call = logger::info;
            call.accept("direct");
            return true;
          };
      var logger = new RecordingLogger();
      logger.enabledLevels.remove(LoggingLevel.INFO);
      invokes.apply(logger);
      assertThat(logger.entries)
          .singleElement()
          .isEqualTo(new RecordingLogger.LogEntry(LoggingLevel.INFO, "direct"));
    }
  }
}
