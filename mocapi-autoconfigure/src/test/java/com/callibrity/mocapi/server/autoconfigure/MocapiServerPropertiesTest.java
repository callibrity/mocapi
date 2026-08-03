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
package com.callibrity.mocapi.server.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiServerPropertiesTest {

  private static MocapiServerProperties propsWithStreamTimeout(Duration streamTimeout) {
    return new MocapiServerProperties(
        "mocapi", "Mocapi", "1.0", null, null, streamTimeout, true, null, null, null);
  }

  @Test
  void
      an_operator_who_never_sets_stream_timeout_still_gets_a_bounded_backstop_not_an_unbounded_hang() {
    // A hung handler that never sends its final response must eventually be aborted even when the
    // operator never configured mocapi.stream-timeout — otherwise a single stuck request leaks a
    // connection (and, for the streamable HTTP transport, a server thread/response stream) forever.
    var props = propsWithStreamTimeout(null);

    assertThat(props.streamTimeoutOrDefault())
        .isEqualTo(MocapiServerProperties.DEFAULT_STREAM_TIMEOUT);
  }

  @Test
  void
      an_operator_configured_stream_timeout_is_honored_rather_than_silently_replaced_by_the_default() {
    var configured = Duration.ofSeconds(30);
    var props = propsWithStreamTimeout(configured);

    assertThat(props.streamTimeoutOrDefault()).isEqualTo(configured);
  }
}
