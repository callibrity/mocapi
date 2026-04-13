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

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ClientResponseIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void clientResultResponseReturns202() throws Exception {
    String sessionId = client.initialize();

    String body =
        """
        {"jsonrpc":"2.0","id":99,"result":{"content":"hello"}}""";

    client
        .postRaw("application/json, text/event-stream", sessionId, body)
        .andExpect(status().isAccepted());
  }

  @Test
  void clientErrorResponseReturns202() throws Exception {
    String sessionId = client.initialize();

    String body =
        """
        {"jsonrpc":"2.0","id":99,"error":{"code":-32600,"message":"rejected"}}""";

    client
        .postRaw("application/json, text/event-stream", sessionId, body)
        .andExpect(status().isAccepted());
  }

  @Test
  void clientResultWithoutSessionIdReturns400() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":99,"result":{"content":"hello"}}""";

    client
        .postRaw("application/json, text/event-stream", null, body)
        .andExpect(status().isBadRequest());
  }

  @Test
  void clientErrorWithoutSessionIdReturns400() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":99,"error":{"code":-32600,"message":"rejected"}}""";

    client
        .postRaw("application/json, text/event-stream", null, body)
        .andExpect(status().isBadRequest());
  }
}
