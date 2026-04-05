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
package com.callibrity.mocapi.autoconfigure.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

class McpStreamEmitterTest {

  @Test
  void sendPrimingEventShouldGenerateEventIdAndStoreIt() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    emitter.sendPrimingEvent();

    var events = session.getStreamEvents().get(emitter.getStreamId());
    assertThat(events).isNotNull().hasSize(1);
    assertThat(events.peek().id()).startsWith(emitter.getStreamId() + ":");
    assertThat(events.peek().data()).isEqualTo("");
  }

  @Test
  void sendShouldStoreEventWithData() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    emitter.send("test-data");

    var events = session.getStreamEvents().get(emitter.getStreamId());
    assertThat(events).isNotNull().hasSize(1);
    assertThat(events.peek().data()).isEqualTo("test-data");
  }

  @Test
  void sendAndCompleteShouldStoreEventAndComplete() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    AtomicBoolean closed = new AtomicBoolean(false);
    emitter.onClose(() -> closed.set(true));
    emitter.sendAndComplete("final-data");

    assertThat(closed.get()).isTrue();
  }

  @Test
  void completeShouldBeIdempotent() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    AtomicInteger closeCount = new AtomicInteger(0);
    emitter.onClose(closeCount::incrementAndGet);

    emitter.complete();
    emitter.complete();

    assertThat(closeCount.get()).isEqualTo(1);
  }

  @Test
  void onCloseListenersShouldBeCalledOnCompletion() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    AtomicBoolean called1 = new AtomicBoolean(false);
    AtomicBoolean called2 = new AtomicBoolean(false);
    emitter.onClose(() -> called1.set(true));
    emitter.onClose(() -> called2.set(true));

    emitter.complete();

    assertThat(called1.get()).isTrue();
    assertThat(called2.get()).isTrue();
  }

  @Test
  void closeListenerExceptionShouldNotPreventOtherListeners() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    AtomicBoolean secondCalled = new AtomicBoolean(false);
    emitter.onClose(
        () -> {
          throw new RuntimeException("listener failure");
        });
    emitter.onClose(() -> secondCalled.set(true));

    emitter.complete();

    assertThat(secondCalled.get()).isTrue();
  }

  @Test
  void completeShouldClearStream() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    emitter.send("data");
    assertThat(session.getStreamEvents()).containsKey(emitter.getStreamId());

    emitter.complete();
    assertThat(session.getStreamEvents()).doesNotContainKey(emitter.getStreamId());
  }

  @Test
  void withTimeoutShouldCreateEmitter() {
    var session = new McpSession();
    var emitter = McpStreamEmitter.withTimeout(session, Duration.ofMinutes(5));

    assertThat(emitter).isNotNull();
    assertThat(emitter.getStreamId()).isNotNull();
    assertThat(emitter.getEmitter()).isNotNull();
  }

  @Test
  void constructorWithZeroTimeoutShouldCreateEmitter() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session, Duration.ZERO);

    assertThat(emitter).isNotNull();
    assertThat(emitter.getStreamId()).isNotNull();
  }

  @Test
  void trySendInternalAfterCompletionShouldBeIgnored() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    emitter.complete();
    emitter.send("ignored");
  }

  @Test
  void sendAfterUnderlyingSseEmitterCompletesShouldTriggerCompleteWithError() {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    streamEmitter.getEmitter().complete();

    AtomicBoolean closed = new AtomicBoolean(false);
    streamEmitter.onClose(() -> closed.set(true));

    streamEmitter.send("data");

    assertThat(closed.get()).isTrue();
  }

  @Test
  void sendAndCompleteWhenSendFailsShouldCompleteWithError() {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    streamEmitter.getEmitter().complete();

    AtomicBoolean closed = new AtomicBoolean(false);
    streamEmitter.onClose(() -> closed.set(true));

    streamEmitter.sendAndComplete("final");

    assertThat(closed.get()).isTrue();
  }

  @Test
  void completeWithErrorShouldBeIdempotent() {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    streamEmitter.getEmitter().complete();

    AtomicInteger closeCount = new AtomicInteger(0);
    streamEmitter.onClose(closeCount::incrementAndGet);

    streamEmitter.send("data1");
    streamEmitter.send("data2");

    assertThat(closeCount.get()).isEqualTo(1);
  }

  // --- Callback tests using reflection to trigger SseEmitter internal callbacks ---

  @Test
  void timeoutCallbackShouldTriggerCompleteWithError() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    AtomicBoolean closed = new AtomicBoolean(false);
    streamEmitter.onClose(() -> closed.set(true));

    Runnable timeoutCallback = getCallback(streamEmitter, "timeoutCallback", Runnable.class);
    timeoutCallback.run();

    assertThat(closed.get()).isTrue();
  }

  @Test
  void errorCallbackShouldTriggerCompleteWithError() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    AtomicBoolean closed = new AtomicBoolean(false);
    streamEmitter.onClose(() -> closed.set(true));

    Field field = ResponseBodyEmitter.class.getDeclaredField("errorCallback");
    field.setAccessible(true);
    Object errorCallback = field.get(streamEmitter.getEmitter());
    // ErrorCallback implements Consumer<Throwable>; invoke via the public Consumer interface
    Method acceptMethod = java.util.function.Consumer.class.getMethod("accept", Object.class);
    acceptMethod.invoke(errorCallback, new RuntimeException("stream error"));

    assertThat(closed.get()).isTrue();
  }

  @Test
  void completionCallbackShouldMarkCompleted() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    streamEmitter.send("data");
    assertThat(session.getStreamEvents()).containsKey(streamEmitter.getStreamId());

    Runnable completionCallback = getCallback(streamEmitter, "completionCallback", Runnable.class);
    completionCallback.run();

    assertThat(session.getStreamEvents()).doesNotContainKey(streamEmitter.getStreamId());
  }

  @Test
  void completeWithErrorIdempotentViaTimeoutAfterComplete() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    AtomicInteger closeCount = new AtomicInteger(0);
    streamEmitter.onClose(closeCount::incrementAndGet);

    streamEmitter.complete();

    Runnable timeoutCallback = getCallback(streamEmitter, "timeoutCallback", Runnable.class);
    timeoutCallback.run();

    assertThat(closeCount.get()).isEqualTo(1);
  }

  // --- Tests using handler proxy to cover exception catch blocks ---

  @Test
  void ioExceptionDuringSendShouldTriggerCompleteWithError() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    initializeWithHandler(
        streamEmitter,
        (method, args) -> {
          if ("send".equals(method.getName())) {
            throw new IOException("broken pipe");
          }
          return null;
        });

    AtomicBoolean closed = new AtomicBoolean(false);
    streamEmitter.onClose(() -> closed.set(true));

    streamEmitter.send("data");

    assertThat(closed.get()).isTrue();
  }

  @Test
  void completeShouldHandleIllegalStateFromSseEmitter() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    initializeWithHandler(
        streamEmitter,
        (method, args) -> {
          if ("complete".equals(method.getName())) {
            throw new IllegalStateException("already completed");
          }
          return null;
        });

    streamEmitter.complete();
  }

  @Test
  void completeWithErrorShouldHandleIllegalStateFromSseEmitter() throws Exception {
    var session = new McpSession();
    var streamEmitter = new McpStreamEmitter(session);

    initializeWithHandler(
        streamEmitter,
        (method, args) -> {
          if ("send".equals(method.getName())) {
            throw new IOException("broken");
          }
          if ("completeWithError".equals(method.getName())) {
            throw new IllegalStateException("already completed");
          }
          return null;
        });

    streamEmitter.send("data");
  }

  // --- Helpers ---

  private static <T> T getCallback(McpStreamEmitter streamEmitter, String fieldName, Class<T> type)
      throws Exception {
    Field field = ResponseBodyEmitter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(streamEmitter.getEmitter()));
  }

  @FunctionalInterface
  interface HandlerAction {
    Object invoke(Method method, Object[] args) throws Throwable;
  }

  private static void initializeWithHandler(McpStreamEmitter streamEmitter, HandlerAction action)
      throws Exception {
    Class<?> handlerClass =
        Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler");
    Object handler =
        Proxy.newProxyInstance(
            handlerClass.getClassLoader(),
            new Class<?>[] {handlerClass},
            (proxy, method, args) -> action.invoke(method, args));
    Method initMethod = ResponseBodyEmitter.class.getDeclaredMethod("initialize", handlerClass);
    initMethod.setAccessible(true);
    initMethod.invoke(streamEmitter.getEmitter(), handler);
  }
}
