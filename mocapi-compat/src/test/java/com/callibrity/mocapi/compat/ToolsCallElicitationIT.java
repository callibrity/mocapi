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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.compat.conformance.ConformanceApplication;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.core.Mailbox;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = ConformanceApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ToolsCallElicitationIT {

  @Autowired private MockMvc mockMvc;

  @MockitoSpyBean private MailboxFactory mailboxFactory;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void elicitationToolReturnsUserResponse() throws Exception {
    ObjectNode capabilities = client.objectMapper().createObjectNode();
    capabilities.putObject("elicitation");
    String sessionId = client.initializeWithCapabilities(capabilities);

    CountDownLatch keyLatch = new CountDownLatch(1);
    AtomicReference<String> capturedKey = new AtomicReference<>();

    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              Class<?> type = invocation.getArgument(1);
              var result = invocation.callRealMethod();
              if (type == JsonRpcResponse.class) {
                capturedKey.set(key);
                keyLatch.countDown();
              }
              return result;
            })
        .when(mailboxFactory)
        .create(anyString(), any(Class.class));

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "test_elicitation");
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("message", "Please provide your info");

    MvcResult mvcResult =
        client
            .post(
                sessionId,
                "tools/call",
                params,
                client.objectMapper().getNodeFactory().numberNode(3))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(keyLatch.await(5, TimeUnit.SECONDS)).isTrue();
    String jsonRpcId = capturedKey.get();

    // Deliver the elicitation response directly to the mailbox
    ObjectNode elicitResult = client.objectMapper().createObjectNode();
    elicitResult.put("action", "accept");
    ObjectNode content = elicitResult.putObject("content");
    content.put("username", "testuser");
    content.put("email", "test@example.com");

    Mailbox<JsonRpcResponse> mailbox = mailboxFactory.create(jsonRpcId, JsonRpcResponse.class);
    mailbox.deliver(new JsonRpcResult(elicitResult, null));

    mvcResult.getAsyncResult(5000);
    String body = mvcResult.getResponse().getContentAsString();

    assertThat(body).contains("action=accept");
    assertThat(body).contains("content=testuser");
  }
}
