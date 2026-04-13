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
package com.callibrity.mocapi.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.mailbox.DefaultMailboxFactory;
import org.jwcarman.substrate.core.memory.mailbox.InMemoryMailboxSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.notifier.Notifier;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import tools.jackson.databind.ObjectMapper;

class McpResponseCorrelationServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ShutdownCoordinator shutdownCoordinator;
  private MailboxFactory mailboxFactory;
  private McpResponseCorrelationService service;

  @BeforeEach
  void setUp() {
    CodecFactory codecFactory = new JacksonCodecFactory(objectMapper);
    Notifier notifier = new DefaultNotifier(new InMemoryNotifier(), codecFactory);
    shutdownCoordinator = new ShutdownCoordinator();
    shutdownCoordinator.start();
    mailboxFactory =
        new DefaultMailboxFactory(
            new InMemoryMailboxSpi(),
            codecFactory,
            notifier,
            Duration.ofMinutes(10),
            shutdownCoordinator);
    service =
        new McpResponseCorrelationService(mailboxFactory, objectMapper, Duration.ofSeconds(5));
  }

  @AfterEach
  void tearDown() {
    shutdownCoordinator.stop();
  }

  @Test
  void sendAndAwaitDeliversCorrelatedResponse() throws Exception {
    var transport = new LatchingTransport(1);

    var resultRef = new AtomicReference<ElicitResult>();
    var thread =
        Thread.ofVirtual()
            .start(
                () -> {
                  resultRef.set(
                      service.sendAndAwait(
                          "elicitation/create",
                          objectMapper.createObjectNode().put("message", "Name?"),
                          ElicitResult.class,
                          transport));
                });

    // Wait for the request to be sent
    assertThat(transport.awaitMessages(5, TimeUnit.SECONDS)).isTrue();

    assertThat(transport.messages()).hasSize(1);
    var sentCall = (JsonRpcCall) transport.messages().getFirst();
    assertThat(sentCall.method()).isEqualTo("elicitation/create");
    String correlationId = sentCall.id().asString();

    // Simulate client response
    var responseContent =
        objectMapper
            .createObjectNode()
            .put("action", "accept")
            .set("content", objectMapper.createObjectNode().put("name", "Alice"));
    service.deliver(new JsonRpcResult(responseContent, objectMapper.valueToTree(correlationId)));

    thread.join(Duration.ofSeconds(5));
    assertThat(thread.isAlive()).isFalse();
    assertThat(resultRef.get().action()).isEqualTo(ElicitAction.ACCEPT);
    assertThat(resultRef.get().content().get("name").asString()).isEqualTo("Alice");
  }

  @Test
  void sendAndAwaitTimesOut() {
    var transport = new LatchingTransport(1);
    var shortTimeoutService =
        new McpResponseCorrelationService(mailboxFactory, objectMapper, Duration.ofMillis(200));

    assertThatThrownBy(
            () ->
                shortTimeoutService.sendAndAwait(
                    "elicitation/create",
                    objectMapper.createObjectNode().put("message", "Hello"),
                    ElicitResult.class,
                    transport))
        .isInstanceOf(McpClientResponseTimeoutException.class)
        .hasMessageContaining("Timed out");
  }

  @Test
  void orphanResponseIsSilentlyDropped() {
    var response =
        new JsonRpcResult(
            objectMapper.createObjectNode(), objectMapper.valueToTree("nonexistent-id"));

    service.deliver(response);
  }

  @Test
  void multipleCorrelationsAreIndependent() throws Exception {
    var transport = new LatchingTransport(2);

    var result0 = new AtomicReference<ElicitResult>();
    var result1 = new AtomicReference<ElicitResult>();

    var thread1 =
        Thread.ofVirtual()
            .start(
                () ->
                    result0.set(
                        service.sendAndAwait(
                            "elicitation/create",
                            objectMapper.createObjectNode().put("id", "1"),
                            ElicitResult.class,
                            transport)));

    var thread2 =
        Thread.ofVirtual()
            .start(
                () ->
                    result1.set(
                        service.sendAndAwait(
                            "elicitation/create",
                            objectMapper.createObjectNode().put("id", "2"),
                            ElicitResult.class,
                            transport)));

    assertThat(transport.awaitMessages(5, TimeUnit.SECONDS)).isTrue();
    assertThat(transport.messages()).hasSize(2);

    // Find each call by its params to match correctly regardless of scheduling order
    JsonRpcCall callForId1 = null;
    JsonRpcCall callForId2 = null;
    for (var msg : transport.messages()) {
      var call = (JsonRpcCall) msg;
      if ("1".equals(call.params().get("id").asString())) {
        callForId1 = call;
      } else {
        callForId2 = call;
      }
    }

    // Deliver response for thread2 first, then thread1
    var contentFor2 =
        objectMapper
            .createObjectNode()
            .put("action", "accept")
            .set("content", objectMapper.createObjectNode().put("value", "second"));
    service.deliver(new JsonRpcResult(contentFor2, callForId2.id()));

    var contentFor1 =
        objectMapper
            .createObjectNode()
            .put("action", "accept")
            .set("content", objectMapper.createObjectNode().put("value", "first"));
    service.deliver(new JsonRpcResult(contentFor1, callForId1.id()));

    thread1.join(Duration.ofSeconds(5));
    thread2.join(Duration.ofSeconds(5));

    assertThat(result0.get().content().get("value").asString()).isEqualTo("first");
    assertThat(result1.get().content().get("value").asString()).isEqualTo("second");
  }

  /** Thread-safe transport with a latch to signal when expected messages have been sent. */
  private static class LatchingTransport implements McpTransport {
    private final List<McpEvent> events = new CopyOnWriteArrayList<>();
    private final List<JsonRpcMessage> messages = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch;

    LatchingTransport(int expectedMessages) {
      this.latch = new CountDownLatch(expectedMessages);
    }

    @Override
    public void emit(McpEvent event) {
      events.add(event);
    }

    @Override
    public void send(JsonRpcMessage message) {
      messages.add(message);
      latch.countDown();
    }

    boolean awaitMessages(long timeout, TimeUnit unit) throws InterruptedException {
      return latch.await(timeout, unit);
    }

    List<JsonRpcMessage> messages() {
      return List.copyOf(messages);
    }
  }
}
