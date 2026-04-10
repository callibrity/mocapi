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
package com.callibrity.mocapi.stream.elicitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ElicitationResultTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // --- ElicitationResult (builder-based) tests ---

  @Test
  void acceptedResultShouldBeAccepted() {
    JsonNode content = objectMapper.valueToTree(Map.of("name", "Alice"));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.isAccepted()).isTrue();
    assertThat(result.action()).isEqualTo(ElicitationAction.ACCEPT);
  }

  @Test
  void declinedResultShouldNotBeAccepted() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThat(result.isAccepted()).isFalse();
    assertThat(result.action()).isEqualTo(ElicitationAction.DECLINE);
  }

  @Test
  void cancelledResultShouldNotBeAccepted() {
    var result = new ElicitationResult(ElicitationAction.CANCEL, null);
    assertThat(result.isAccepted()).isFalse();
    assertThat(result.action()).isEqualTo(ElicitationAction.CANCEL);
  }

  @Test
  void getStringShouldReturnStringValue() {
    JsonNode content = objectMapper.valueToTree(Map.of("name", "Alice"));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getString("name")).isEqualTo("Alice");
  }

  @Test
  void getIntegerShouldReturnIntValue() {
    JsonNode content = objectMapper.valueToTree(Map.of("age", 30));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getInteger("age")).isEqualTo(30);
  }

  @Test
  void getNumberShouldReturnDoubleValue() {
    JsonNode content = objectMapper.valueToTree(Map.of("score", 3.14));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getNumber("score")).isEqualTo(3.14);
  }

  @Test
  void getBoolShouldReturnBooleanValue() {
    JsonNode content = objectMapper.valueToTree(Map.of("active", true));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getBool("active")).isTrue();
  }

  enum Color {
    RED,
    GREEN,
    BLUE
  }

  @Test
  void getChoiceShouldReturnStringValue() {
    JsonNode content = objectMapper.valueToTree(Map.of("status", "active"));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getChoice("status")).isEqualTo("active");
  }

  @Test
  void getChoicesShouldReturnStringList() {
    JsonNode content = objectMapper.valueToTree(Map.of("roles", List.of("admin", "user")));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getChoices("roles")).containsExactly("admin", "user");
  }

  @Test
  void getChoiceOnDeclinedShouldThrowForStringOverload() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getChoice("status"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getChoicesOnDeclinedShouldThrowForStringOverload() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getChoices("roles"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getChoiceShouldReturnEnumValue() {
    JsonNode content = objectMapper.valueToTree(Map.of("color", "GREEN"));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getChoice("color", Color.class)).isEqualTo(Color.GREEN);
  }

  @Test
  void getChoicesShouldReturnEnumList() {
    JsonNode content = objectMapper.valueToTree(Map.of("colors", List.of("RED", "BLUE")));
    var result = new ElicitationResult(ElicitationAction.ACCEPT, content);
    assertThat(result.getChoices("colors", Color.class)).containsExactly(Color.RED, Color.BLUE);
  }

  @Test
  void getStringOnDeclinedShouldThrow() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getString("name"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getIntegerOnCancelledShouldThrow() {
    var result = new ElicitationResult(ElicitationAction.CANCEL, null);
    assertThatThrownBy(() -> result.getInteger("age"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getNumberOnDeclinedShouldThrow() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getNumber("score"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getBoolOnDeclinedShouldThrow() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getBool("active"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getChoiceOnDeclinedShouldThrow() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getChoice("color", Color.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  @Test
  void getChoicesOnDeclinedShouldThrow() {
    var result = new ElicitationResult(ElicitationAction.DECLINE, null);
    assertThatThrownBy(() -> result.getChoices("colors", Color.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not accepted");
  }

  // --- BeanElicitationResult tests ---

  @Test
  void beanAcceptedResultShouldHaveContent() {
    var result = new BeanElicitationResult<>(ElicitationAction.ACCEPT, "hello");
    assertThat(result.accepted()).isTrue();
    assertThat(result.declined()).isFalse();
    assertThat(result.cancelled()).isFalse();
    assertThat(result.content()).isEqualTo("hello");
  }

  @Test
  void beanDeclinedResultShouldHaveNullContent() {
    var result = new BeanElicitationResult<String>(ElicitationAction.DECLINE, null);
    assertThat(result.declined()).isTrue();
    assertThat(result.accepted()).isFalse();
    assertThat(result.cancelled()).isFalse();
    assertThat(result.content()).isNull();
  }

  @Test
  void beanCancelledResultShouldHaveNullContent() {
    var result = new BeanElicitationResult<String>(ElicitationAction.CANCEL, null);
    assertThat(result.cancelled()).isTrue();
    assertThat(result.accepted()).isFalse();
    assertThat(result.declined()).isFalse();
    assertThat(result.content()).isNull();
  }

  // --- ElicitationAction tests ---

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
