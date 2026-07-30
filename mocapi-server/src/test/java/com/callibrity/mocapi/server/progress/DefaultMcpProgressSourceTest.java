/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ValueNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMcpProgressSourceTest {

  private static final ValueNode TOKEN = JsonNodeFactory.instance.stringNode("tok-1");

  private final McpTransport transport = mock(McpTransport.class);

  private DefaultMcpProgressSource source(ValueNode token) {
    return new DefaultMcpProgressSource(transport, token);
  }

  private List<JsonRpcNotification> capturedNotifications() {
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, org.mockito.Mockito.atLeast(0)).send(captor.capture());
    return captor.getAllValues().stream().map(JsonRpcNotification.class::cast).toList();
  }

  @Nested
  class Double_progress {

    @Test
    void sends_floating_point_progress_and_total_with_message() {
      source(TOKEN).doubleProgress(2.5).emit(1.25, "quarter");

      var n = capturedNotifications().get(0);
      assertThat(n.method()).isEqualTo("notifications/progress");
      assertThat(n.params().get("progress").isFloatingPointNumber()).isTrue();
      assertThat(n.params().get("progress").asDouble()).isEqualTo(1.25);
      assertThat(n.params().get("total").isFloatingPointNumber()).isTrue();
      assertThat(n.params().get("total").asDouble()).isEqualTo(2.5);
      assertThat(n.params().get("message").asString()).isEqualTo("quarter");
    }

    @Test
    void emits_floating_point_even_for_whole_values() {
      source(TOKEN).doubleProgress(4.0).emit(2.0);

      var n = capturedNotifications().get(0);
      assertThat(n.params().get("progress").isFloatingPointNumber()).isTrue();
      assertThat(n.params().get("total").isFloatingPointNumber()).isTrue();
    }

    @Test
    void omits_total_when_unknown() {
      source(TOKEN).doubleProgress(null).emit(3.0);

      var n = capturedNotifications().get(0);
      assertThat(n.params().has("total")).isFalse();
      assertThat(n.params().has("message")).isFalse();
    }

    @Test
    void throws_when_progress_does_not_increase() {
      var emitter = source(TOKEN).doubleProgress(10.0);
      emitter.emit(5.0);

      assertThatThrownBy(() -> emitter.emit(5.0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly increase");
    }
  }

  @Nested
  class Long_progress {

    @Test
    void emits_integral_progress_and_total_on_the_wire() {
      source(TOKEN).longProgress(4L).emit(2, "halfway");

      var n = capturedNotifications().get(0);
      assertThat(n.params().get("progress").isIntegralNumber()).isTrue();
      assertThat(n.params().get("progress").asLong()).isEqualTo(2L);
      assertThat(n.params().get("total").isIntegralNumber()).isTrue();
      assertThat(n.params().get("total").asLong()).isEqualTo(4L);
    }
  }

  @Nested
  class Counting_progress {

    @Test
    void advances_by_one_on_each_emit() {
      var counter = source(TOKEN).countingProgress(3L);
      counter.emit("first");
      counter.emit("second");

      var notifications = capturedNotifications();
      assertThat(notifications.get(0).params().get("progress").isIntegralNumber()).isTrue();
      assertThat(notifications.get(0).params().get("progress").asLong()).isEqualTo(1L);
      assertThat(notifications.get(0).params().get("message").asString()).isEqualTo("first");
      assertThat(notifications.get(1).params().get("progress").asLong()).isEqualTo(2L);
      assertThat(notifications.get(0).params().get("total").isIntegralNumber()).isTrue();
      assertThat(notifications.get(0).params().get("total").asLong()).isEqualTo(3L);
    }

    @Test
    void emits_without_a_message() {
      source(TOKEN).countingProgress(null).emit();

      var n = capturedNotifications().get(0);
      assertThat(n.params().get("progress").isIntegralNumber()).isTrue();
      assertThat(n.params().get("progress").asLong()).isEqualTo(1L);
      assertThat(n.params().has("total")).isFalse();
      assertThat(n.params().has("message")).isFalse();
    }
  }

  @Nested
  class Percent_progress {

    @Test
    void reports_fraction_against_a_fixed_total_of_one() {
      source(TOKEN).percentProgress().complete(0.25, "a quarter");

      var n = capturedNotifications().get(0);
      assertThat(n.params().get("progress").asDouble()).isEqualTo(0.25);
      assertThat(n.params().get("total").isFloatingPointNumber()).isTrue();
      assertThat(n.params().get("total").asDouble()).isEqualTo(1.0);
    }

    @Test
    void throws_when_fraction_is_below_zero() {
      var emitter = source(TOKEN).percentProgress();
      assertThatThrownBy(() -> emitter.complete(-0.1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("[0.0, 1.0]");
    }

    @Test
    void throws_when_fraction_is_above_one() {
      var emitter = source(TOKEN).percentProgress();
      assertThatThrownBy(() -> emitter.complete(1.5))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("[0.0, 1.0]");
    }
  }

  @Nested
  class Without_a_progress_token {

    @Test
    void validates_but_sends_nothing() {
      var emitter = source(null).doubleProgress(10.0);

      emitter.emit(5.0); // accepted, nothing sent

      assertThatThrownBy(() -> emitter.emit(5.0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly increase");
      verifyNoInteractions(transport);
    }
  }
}
