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
package com.callibrity.mocapi.server.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.SetLevelRequestParams;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionService;
import org.junit.jupiter.api.Test;

class McpLoggingServiceTest {

  @Test
  void setLevelDelegatesToSessionService() {
    McpSessionService sessionService = mock(McpSessionService.class);
    McpLoggingService service = new McpLoggingService(sessionService);
    McpSession session = new McpSession("sess-1", "2025-11-25", null, null, LoggingLevel.WARNING);

    var result = service.setLevel(session, new SetLevelRequestParams(LoggingLevel.DEBUG, null));

    assertThat(result).isNotNull();
    verify(sessionService).setLogLevel("sess-1", LoggingLevel.DEBUG);
  }
}
