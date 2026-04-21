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

import com.callibrity.mocapi.model.LoggingLevel;
import org.slf4j.helpers.MessageFormatter;

/**
 * SLF4J-shaped logger that routes messages through the current {@link McpToolContext}. Obtain via
 * {@link McpToolContext#logger(String)} or {@link McpToolContext#logger()}. Parameterized-format
 * overloads use SLF4J's {@code {}} placeholder syntax.
 *
 * <p>Implementers only need to provide {@link #isEnabled(LoggingLevel)} and {@link
 * #log(LoggingLevel, String)}; the per-level {@code isXxxEnabled()} / {@code xxx(...)} methods are
 * defaults that delegate to those two.
 */
public interface McpLogger {

  /** Returns true if messages at {@code level} would be forwarded to the client. */
  boolean isEnabled(LoggingLevel level);

  /**
   * Forwards {@code message} at {@code level} to the client. Implementers should short-circuit
   * internally when {@link #isEnabled(LoggingLevel)} is false.
   */
  void log(LoggingLevel level, String message);

  default boolean isDebugEnabled() {
    return isEnabled(LoggingLevel.DEBUG);
  }

  default boolean isInfoEnabled() {
    return isEnabled(LoggingLevel.INFO);
  }

  default boolean isNoticeEnabled() {
    return isEnabled(LoggingLevel.NOTICE);
  }

  default boolean isWarnEnabled() {
    return isEnabled(LoggingLevel.WARNING);
  }

  default boolean isErrorEnabled() {
    return isEnabled(LoggingLevel.ERROR);
  }

  default boolean isCriticalEnabled() {
    return isEnabled(LoggingLevel.CRITICAL);
  }

  default boolean isAlertEnabled() {
    return isEnabled(LoggingLevel.ALERT);
  }

  default boolean isEmergencyEnabled() {
    return isEnabled(LoggingLevel.EMERGENCY);
  }

  default void debug(String message) {
    log(LoggingLevel.DEBUG, message);
  }

  default void debug(String format, Object... args) {
    if (isDebugEnabled()) log(LoggingLevel.DEBUG, format(format, args));
  }

  default void info(String message) {
    log(LoggingLevel.INFO, message);
  }

  default void info(String format, Object... args) {
    if (isInfoEnabled()) log(LoggingLevel.INFO, format(format, args));
  }

  default void notice(String message) {
    log(LoggingLevel.NOTICE, message);
  }

  default void notice(String format, Object... args) {
    if (isNoticeEnabled()) log(LoggingLevel.NOTICE, format(format, args));
  }

  default void warn(String message) {
    log(LoggingLevel.WARNING, message);
  }

  default void warn(String format, Object... args) {
    if (isWarnEnabled()) log(LoggingLevel.WARNING, format(format, args));
  }

  default void error(String message) {
    log(LoggingLevel.ERROR, message);
  }

  default void error(String format, Object... args) {
    if (isErrorEnabled()) log(LoggingLevel.ERROR, format(format, args));
  }

  default void critical(String message) {
    log(LoggingLevel.CRITICAL, message);
  }

  default void critical(String format, Object... args) {
    if (isCriticalEnabled()) log(LoggingLevel.CRITICAL, format(format, args));
  }

  default void alert(String message) {
    log(LoggingLevel.ALERT, message);
  }

  default void alert(String format, Object... args) {
    if (isAlertEnabled()) log(LoggingLevel.ALERT, format(format, args));
  }

  default void emergency(String message) {
    log(LoggingLevel.EMERGENCY, message);
  }

  default void emergency(String format, Object... args) {
    if (isEmergencyEnabled()) log(LoggingLevel.EMERGENCY, format(format, args));
  }

  private static String format(String pattern, Object... args) {
    return MessageFormatter.arrayFormat(pattern, args).getMessage();
  }
}
