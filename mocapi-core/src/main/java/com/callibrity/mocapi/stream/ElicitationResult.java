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
package com.callibrity.mocapi.stream;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The result of a builder-based MCP elicitation request. Contains the client's action and provides
 * typed getters for accessing individual fields from the response content. This is the return type
 * of {@link McpStreamContext#elicit(String, java.util.function.Consumer)}.
 */
public final class ElicitationResult {

  private final ElicitationAction action;
  private final JsonNode content;

  public ElicitationResult(ElicitationAction action, JsonNode content) {
    this.action = action;
    this.content = content;
  }

  /** Returns the action taken by the client. */
  public ElicitationAction action() {
    return action;
  }

  /** Returns true if the client accepted the elicitation and provided content. */
  public boolean isAccepted() {
    return action == ElicitationAction.ACCEPT;
  }

  /** Returns the string value of the named property. */
  public String getString(String name) {
    requireAccepted();
    return content.get(name).asString();
  }

  /** Returns the integer value of the named property. */
  public int getInteger(String name) {
    requireAccepted();
    return content.get(name).asInt();
  }

  /** Returns the double value of the named property. */
  public double getNumber(String name) {
    requireAccepted();
    return content.get(name).asDouble();
  }

  /** Returns the boolean value of the named property. */
  public boolean getBool(String name) {
    requireAccepted();
    return content.get(name).asBoolean();
  }

  /** Returns the string value of the named single-select property. */
  public String getChoice(String name) {
    requireAccepted();
    return content.get(name).asString();
  }

  /** Returns the enum value of the named single-select property. */
  public <E extends Enum<E>> E getChoice(String name, Class<E> enumType) {
    requireAccepted();
    return Enum.valueOf(enumType, content.get(name).asString());
  }

  /** Returns the list of string values of the named multi-select property. */
  public List<String> getChoices(String name) {
    requireAccepted();
    JsonNode arrayNode = content.get(name);
    List<String> result = new ArrayList<>();
    for (JsonNode element : arrayNode) {
      result.add(element.asString());
    }
    return result;
  }

  /** Returns the list of enum values of the named multi-select property. */
  public <E extends Enum<E>> List<E> getChoices(String name, Class<E> enumType) {
    requireAccepted();
    JsonNode arrayNode = content.get(name);
    List<E> result = new ArrayList<>();
    for (JsonNode element : arrayNode) {
      result.add(Enum.valueOf(enumType, element.asString()));
    }
    return result;
  }

  private void requireAccepted() {
    if (action != ElicitationAction.ACCEPT) {
      throw new IllegalStateException(
          "Cannot access content: elicitation was not accepted (action=" + action + ")");
    }
  }
}
