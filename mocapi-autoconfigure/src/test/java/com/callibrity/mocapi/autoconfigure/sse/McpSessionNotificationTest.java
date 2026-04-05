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

import org.junit.jupiter.api.Test;

class McpSessionNotificationTest {

  @Test
  void shouldRegisterNotificationEmitter() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    session.registerNotificationEmitter(emitter);
    assertThat(session.getNotificationEmitterCount()).isEqualTo(1);
  }

  @Test
  void shouldRemoveEmitterOnCompletion() {
    var session = new McpSession();
    var emitter = new McpStreamEmitter(session);

    session.registerNotificationEmitter(emitter);
    assertThat(session.getNotificationEmitterCount()).isEqualTo(1);

    emitter.complete();
    assertThat(session.getNotificationEmitterCount()).isZero();
  }

  @Test
  void shouldSupportMultipleEmitters() {
    var session = new McpSession();
    var emitter1 = new McpStreamEmitter(session);
    var emitter2 = new McpStreamEmitter(session);

    session.registerNotificationEmitter(emitter1);
    session.registerNotificationEmitter(emitter2);
    assertThat(session.getNotificationEmitterCount()).isEqualTo(2);

    emitter1.complete();
    assertThat(session.getNotificationEmitterCount()).isEqualTo(1);
  }

  @Test
  void shouldStartWithZeroEmitters() {
    var session = new McpSession();
    assertThat(session.getNotificationEmitterCount()).isZero();
  }
}
