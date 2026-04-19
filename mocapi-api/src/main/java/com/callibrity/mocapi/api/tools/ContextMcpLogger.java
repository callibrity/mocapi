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

/** Default {@link McpLogger} implementation that delegates to an {@link McpToolContext}. */
final class ContextMcpLogger implements McpLogger {

  private final McpToolContext ctx;
  private final String loggerName;

  ContextMcpLogger(McpToolContext ctx, String loggerName) {
    this.ctx = ctx;
    this.loggerName = loggerName;
  }

  @Override
  public boolean isDebugEnabled() {
    return ctx.isEnabled(LoggingLevel.DEBUG);
  }

  @Override
  public boolean isInfoEnabled() {
    return ctx.isEnabled(LoggingLevel.INFO);
  }

  @Override
  public boolean isNoticeEnabled() {
    return ctx.isEnabled(LoggingLevel.NOTICE);
  }

  @Override
  public boolean isWarnEnabled() {
    return ctx.isEnabled(LoggingLevel.WARNING);
  }

  @Override
  public boolean isErrorEnabled() {
    return ctx.isEnabled(LoggingLevel.ERROR);
  }

  @Override
  public boolean isCriticalEnabled() {
    return ctx.isEnabled(LoggingLevel.CRITICAL);
  }

  @Override
  public boolean isAlertEnabled() {
    return ctx.isEnabled(LoggingLevel.ALERT);
  }

  @Override
  public boolean isEmergencyEnabled() {
    return ctx.isEnabled(LoggingLevel.EMERGENCY);
  }

  @Override
  public void debug(String message) {
    if (isDebugEnabled()) ctx.log(LoggingLevel.DEBUG, loggerName, message);
  }

  @Override
  public void debug(String format, Object... args) {
    if (isDebugEnabled()) ctx.log(LoggingLevel.DEBUG, loggerName, format(format, args));
  }

  @Override
  public void info(String message) {
    if (isInfoEnabled()) ctx.log(LoggingLevel.INFO, loggerName, message);
  }

  @Override
  public void info(String format, Object... args) {
    if (isInfoEnabled()) ctx.log(LoggingLevel.INFO, loggerName, format(format, args));
  }

  @Override
  public void notice(String message) {
    if (isNoticeEnabled()) ctx.log(LoggingLevel.NOTICE, loggerName, message);
  }

  @Override
  public void notice(String format, Object... args) {
    if (isNoticeEnabled()) ctx.log(LoggingLevel.NOTICE, loggerName, format(format, args));
  }

  @Override
  public void warn(String message) {
    if (isWarnEnabled()) ctx.log(LoggingLevel.WARNING, loggerName, message);
  }

  @Override
  public void warn(String format, Object... args) {
    if (isWarnEnabled()) ctx.log(LoggingLevel.WARNING, loggerName, format(format, args));
  }

  @Override
  public void error(String message) {
    if (isErrorEnabled()) ctx.log(LoggingLevel.ERROR, loggerName, message);
  }

  @Override
  public void error(String format, Object... args) {
    if (isErrorEnabled()) ctx.log(LoggingLevel.ERROR, loggerName, format(format, args));
  }

  @Override
  public void critical(String message) {
    if (isCriticalEnabled()) ctx.log(LoggingLevel.CRITICAL, loggerName, message);
  }

  @Override
  public void critical(String format, Object... args) {
    if (isCriticalEnabled()) ctx.log(LoggingLevel.CRITICAL, loggerName, format(format, args));
  }

  @Override
  public void alert(String message) {
    if (isAlertEnabled()) ctx.log(LoggingLevel.ALERT, loggerName, message);
  }

  @Override
  public void alert(String format, Object... args) {
    if (isAlertEnabled()) ctx.log(LoggingLevel.ALERT, loggerName, format(format, args));
  }

  @Override
  public void emergency(String message) {
    if (isEmergencyEnabled()) ctx.log(LoggingLevel.EMERGENCY, loggerName, message);
  }

  @Override
  public void emergency(String format, Object... args) {
    if (isEmergencyEnabled()) ctx.log(LoggingLevel.EMERGENCY, loggerName, format(format, args));
  }

  private static String format(String pattern, Object... args) {
    return MessageFormatter.arrayFormat(pattern, args).getMessage();
  }
}
