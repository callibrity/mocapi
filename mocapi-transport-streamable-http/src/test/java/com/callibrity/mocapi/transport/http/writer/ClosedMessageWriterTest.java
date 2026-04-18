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
package com.callibrity.mocapi.transport.http.writer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClosedMessageWriterTest {

  @Test
  void write_rejects_response_with_illegal_state() {
    var response =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    assertThatThrownBy(() -> ClosedMessageWriter.INSTANCE.write(response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed response");
  }

  @Test
  void write_rejects_notification_with_illegal_state() {
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    assertThatThrownBy(() -> ClosedMessageWriter.INSTANCE.write(notification))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed response");
  }

  @Test
  void write_rejects_call_with_illegal_state() {
    var call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));

    assertThatThrownBy(() -> ClosedMessageWriter.INSTANCE.write(call))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed response");
  }
}
