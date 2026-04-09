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

/**
 * The result of a bean-typed MCP elicitation request. Contains the client's action and, for
 * accepted responses, the typed content deserialized from the client's form data. This is the
 * return type of {@link McpStreamContext#elicitForm(String, Class)}.
 *
 * @param action the action taken by the client
 * @param content the typed content (null for decline/cancel actions)
 * @param <B> the bean type of the elicitation content
 */
public record BeanElicitationResult<B>(ElicitationAction action, B content) {

  /** Returns true if the client accepted the elicitation and provided content. */
  public boolean accepted() {
    return action == ElicitationAction.ACCEPT;
  }

  /** Returns true if the client declined the elicitation. */
  public boolean declined() {
    return action == ElicitationAction.DECLINE;
  }

  /** Returns true if the client cancelled the elicitation. */
  public boolean cancelled() {
    return action == ElicitationAction.CANCEL;
  }
}
