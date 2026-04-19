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
package com.callibrity.mocapi.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Attaches a Logback {@link ListAppender} to a target logger so tests can assert on log output. */
public final class LogCaptor implements AutoCloseable {

  private final Logger logger;
  private final ListAppender<ILoggingEvent> appender;

  private LogCaptor(Logger target) {
    this.logger = target;
    this.appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
  }

  public static LogCaptor forClass(Class<?> target) {
    return new LogCaptor((Logger) LoggerFactory.getLogger(target));
  }

  public static LogCaptor forName(String loggerName) {
    return new LogCaptor((Logger) LoggerFactory.getLogger(loggerName));
  }

  public List<ILoggingEvent> events() {
    return List.copyOf(appender.list);
  }

  public List<String> formattedMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
