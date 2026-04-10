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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Result of a {@code elicitation/create} request from the client. Contains the action taken by the
 * user and, for accepted responses, the structured content they provided. Typed accessor methods
 * are provided for convenient extraction of individual fields from the content object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitResult(ElicitAction action, ObjectNode content) {

  /** Returns true if the user accepted the elicitation and provided content. */
  public boolean isAccepted() {
    return action == ElicitAction.ACCEPT;
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
    if (action != ElicitAction.ACCEPT) {
      throw new IllegalStateException(
          "Cannot access content: elicitation was not accepted (action=" + action + ")");
    }
  }
}
