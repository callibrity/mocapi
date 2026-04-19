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

/**
 * SLF4J-shaped logger that routes messages through the current {@link McpToolContext}. Obtain via
 * {@link McpToolContext#logger(String)} or {@link McpToolContext#logger()}. Parameterized-format
 * overloads use SLF4J's {@code {}} placeholder syntax.
 */
public interface McpLogger {

  /** Returns true if DEBUG-level messages will be forwarded to the client. */
  boolean isDebugEnabled();

  /** Returns true if INFO-level messages will be forwarded to the client. */
  boolean isInfoEnabled();

  /** Returns true if NOTICE-level messages will be forwarded to the client. */
  boolean isNoticeEnabled();

  /** Returns true if WARNING-level messages will be forwarded to the client. */
  boolean isWarnEnabled();

  /** Returns true if ERROR-level messages will be forwarded to the client. */
  boolean isErrorEnabled();

  /** Returns true if CRITICAL-level messages will be forwarded to the client. */
  boolean isCriticalEnabled();

  /** Returns true if ALERT-level messages will be forwarded to the client. */
  boolean isAlertEnabled();

  /** Returns true if EMERGENCY-level messages will be forwarded to the client. */
  boolean isEmergencyEnabled();

  /** Logs a DEBUG-level message. */
  void debug(String message);

  /** Logs a DEBUG-level message, formatting {@code {}} placeholders in {@code format}. */
  void debug(String format, Object... args);

  /** Logs an INFO-level message. */
  void info(String message);

  /** Logs an INFO-level message, formatting {@code {}} placeholders in {@code format}. */
  void info(String format, Object... args);

  /** Logs a NOTICE-level message. */
  void notice(String message);

  /** Logs a NOTICE-level message, formatting {@code {}} placeholders in {@code format}. */
  void notice(String format, Object... args);

  /** Logs a WARNING-level message. */
  void warn(String message);

  /** Logs a WARNING-level message, formatting {@code {}} placeholders in {@code format}. */
  void warn(String format, Object... args);

  /** Logs an ERROR-level message. */
  void error(String message);

  /** Logs an ERROR-level message, formatting {@code {}} placeholders in {@code format}. */
  void error(String format, Object... args);

  /** Logs a CRITICAL-level message. */
  void critical(String message);

  /** Logs a CRITICAL-level message, formatting {@code {}} placeholders in {@code format}. */
  void critical(String format, Object... args);

  /** Logs an ALERT-level message. */
  void alert(String message);

  /** Logs an ALERT-level message, formatting {@code {}} placeholders in {@code format}. */
  void alert(String format, Object... args);

  /** Logs an EMERGENCY-level message. */
  void emergency(String message);

  /** Logs an EMERGENCY-level message, formatting {@code {}} placeholders in {@code format}. */
  void emergency(String format, Object... args);
}
