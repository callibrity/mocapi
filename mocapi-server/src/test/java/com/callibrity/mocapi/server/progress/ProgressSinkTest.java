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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProgressSinkTest {

  @Test
  void custom_sink_receives_progress_total_and_message() {
    List<String> seen = new ArrayList<>();
    var source =
        new DefaultMcpProgressSource(
            (progress, total, message) -> seen.add(progress + "/" + total + ":" + message));
    var emitter = source.longProgress(10L);
    emitter.emit(3, "chunk a");
    emitter.emit(7);
    assertThat(seen).containsExactly("3/10:chunk a", "7/10:null");
  }

  @Test
  void monotonic_guard_still_applies_with_custom_sink() {
    var source = new DefaultMcpProgressSource((progress, total, message) -> {});
    var emitter = source.longProgress(null);
    emitter.emit(5);
    assertThatThrownBy(() -> emitter.emit(5)).isInstanceOf(IllegalArgumentException.class);
  }
}
