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

import org.junit.jupiter.api.Test;

class ElicitationResultTest {

  @Test
  void acceptedResultShouldHaveContent() {
    var result = new ElicitationResult<>(ElicitationAction.ACCEPT, "hello");
    assertThat(result.accepted()).isTrue();
    assertThat(result.declined()).isFalse();
    assertThat(result.cancelled()).isFalse();
    assertThat(result.content()).isEqualTo("hello");
  }

  @Test
  void declinedResultShouldHaveNullContent() {
    var result = new ElicitationResult<String>(ElicitationAction.DECLINE, null);
    assertThat(result.declined()).isTrue();
    assertThat(result.accepted()).isFalse();
    assertThat(result.cancelled()).isFalse();
    assertThat(result.content()).isNull();
  }

  @Test
  void cancelledResultShouldHaveNullContent() {
    var result = new ElicitationResult<String>(ElicitationAction.CANCEL, null);
    assertThat(result.cancelled()).isTrue();
    assertThat(result.accepted()).isFalse();
    assertThat(result.declined()).isFalse();
    assertThat(result.content()).isNull();
  }

  @Test
  void elicitationActionFromValueShouldParseCorrectly() {
    assertThat(ElicitationAction.fromValue("accept")).isEqualTo(ElicitationAction.ACCEPT);
    assertThat(ElicitationAction.fromValue("decline")).isEqualTo(ElicitationAction.DECLINE);
    assertThat(ElicitationAction.fromValue("cancel")).isEqualTo(ElicitationAction.CANCEL);
  }

  @Test
  void elicitationActionFromValueShouldThrowOnUnknown() {
    assertThatThrownBy(() -> ElicitationAction.fromValue("unknown"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void elicitationActionGetValueShouldReturnLowercase() {
    assertThat(ElicitationAction.ACCEPT.getValue()).isEqualTo("accept");
    assertThat(ElicitationAction.DECLINE.getValue()).isEqualTo("decline");
    assertThat(ElicitationAction.CANCEL.getValue()).isEqualTo("cancel");
  }
}
