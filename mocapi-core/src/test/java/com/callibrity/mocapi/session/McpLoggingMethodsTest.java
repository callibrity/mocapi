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
package com.callibrity.mocapi.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.EmptyResult;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.SetLevelRequestParams;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import org.junit.jupiter.api.Test;

class McpLoggingMethodsTest {

  private final McpSessionService sessionService = mock(McpSessionService.class);
  private final McpLoggingMethods loggingMethods = new McpLoggingMethods(sessionService);

  private static McpSession testSession(String sessionId) {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(sessionId);
  }

  @Test
  void setLevelShouldDelegateToSessionService() throws Exception {
    McpSession session = testSession("session-1");
    var result =
        ScopedValue.where(McpSession.CURRENT, session)
            .call(
                () -> loggingMethods.setLevel(new SetLevelRequestParams(LoggingLevel.DEBUG, null)));
    assertThat(result).isSameAs(EmptyResult.INSTANCE);
    verify(sessionService).setLogLevel("session-1", LoggingLevel.DEBUG);
  }

  @Test
  void setLevelShouldThrowWhenParamsIsNull() {
    McpSession session = testSession("session-1");
    assertThatThrownBy(
            () ->
                ScopedValue.where(McpSession.CURRENT, session)
                    .call(() -> loggingMethods.setLevel(null)))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("null");
  }

  @Test
  void setLevelShouldThrowWhenLevelIsNull() {
    McpSession session = testSession("session-1");
    assertThatThrownBy(
            () ->
                ScopedValue.where(McpSession.CURRENT, session)
                    .call(() -> loggingMethods.setLevel(new SetLevelRequestParams(null, null))))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("null");
  }

  @Test
  void setLevelShouldWrapIllegalArgumentException() {
    McpSession session = testSession("session-1");
    doThrow(new IllegalArgumentException("bad level"))
        .when(sessionService)
        .setLogLevel("session-1", LoggingLevel.ERROR);
    assertThatThrownBy(
            () ->
                ScopedValue.where(McpSession.CURRENT, session)
                    .call(
                        () ->
                            loggingMethods.setLevel(
                                new SetLevelRequestParams(LoggingLevel.ERROR, null))))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("bad level");
  }
}
