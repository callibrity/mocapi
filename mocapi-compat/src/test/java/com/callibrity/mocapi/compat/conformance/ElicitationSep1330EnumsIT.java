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
package com.callibrity.mocapi.compat.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.compat.McpClient;
import com.callibrity.mocapi.compat.RandomMasterKeyInitializer;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = ConformanceApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ElicitationSep1330EnumsIT {

  @Autowired private MockMvc mockMvc;

  @MockitoSpyBean private MailboxFactory mailboxFactory;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void elicitationWithEnumVariantsReturnsAccepted() throws Exception {
    ObjectNode capabilities = client.objectMapper().createObjectNode();
    capabilities.putObject("elicitation");
    String sessionId = client.initializeWithCapabilities(capabilities);

    CountDownLatch keyLatch = new CountDownLatch(1);
    AtomicReference<String> capturedKey = new AtomicReference<>();

    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              var result = invocation.callRealMethod();
              if (key.startsWith("elicit:")) {
                capturedKey.set(key);
                keyLatch.countDown();
              }
              return result;
            })
        .when(mailboxFactory)
        .create(anyString(), any(Class.class));

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "test_elicitation_sep1330_enums");
    params.putObject("arguments");

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
    String jsonRpcId = capturedKey.get().replace("elicit:", "");

    ObjectNode elicitResult = client.objectMapper().createObjectNode();
    elicitResult.put("action", "accept");
    ObjectNode content = elicitResult.putObject("content");
    content.put("untitled_single", "option1");
    content.put("titled_single", "value1");
    content.set(
        "untitled_multi", client.objectMapper().createArrayNode().add("option1").add("option2"));
    content.set(
        "titled_multi", client.objectMapper().createArrayNode().add("value1").add("value2"));

    Mailbox<JsonNode> mailbox = mailboxFactory.create("elicit:" + jsonRpcId, JsonNode.class);
    mailbox.deliver(elicitResult);

    mvcResult.getAsyncResult(5000);
    String body = mvcResult.getResponse().getContentAsString();

    assertThat(body).contains("action=accept");
  }
}
