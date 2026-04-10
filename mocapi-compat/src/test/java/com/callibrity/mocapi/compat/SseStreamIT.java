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
package com.callibrity.mocapi.compat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CompatApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class SseStreamIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void getWithoutSseAcceptReturns406() throws Exception {
    String sessionId = client.initialize();

    client.getRaw("application/json", sessionId).andExpect(status().isNotAcceptable());
  }

  @Test
  void getWithNoAcceptHeaderReturns406() throws Exception {
    String sessionId = client.initialize();

    client.getRaw(null, sessionId).andExpect(status().isNotAcceptable());
  }

  @Test
  void getWithSseAcceptIsAccepted() throws Exception {
    String sessionId = client.initialize();

    client.get(sessionId).andExpect(status().isOk());
  }

  @Test
  void getWithoutSessionReturns400() throws Exception {
    client.getRaw("text/event-stream", null).andExpect(status().isBadRequest());
  }

  @Test
  void getWithUnknownSessionReturns404() throws Exception {
    client.getRaw("text/event-stream", "nonexistent-session-id").andExpect(status().isNotFound());
  }

  @Test
  void tamperedLastEventIdReturns400() throws Exception {
    String sessionId = client.initialize();

    client.get(sessionId).andExpect(status().isOk());

    client.getWithLastEventId(sessionId, "tampered-event-id").andExpect(status().isBadRequest());
  }

  @Test
  void multipleGetSseStreamsAreAccepted() throws Exception {
    String sessionId = client.initialize();

    client.get(sessionId).andExpect(status().isOk());
    client.get(sessionId).andExpect(status().isOk());
  }
}
