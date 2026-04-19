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
package com.callibrity.mocapi.o11y;

import io.micrometer.observation.Observation;

/**
 * {@link Observation.Context} subclass carrying the MCP handler's kind (tool / prompt / resource /
 * resource_template) and name (tool name, prompt name, resource URI, or URI template) so downstream
 * {@link io.micrometer.observation.ObservationConvention ObservationConventions} and {@link
 * io.micrometer.observation.ObservationHandler ObservationHandlers} can access them without reading
 * the tags off the key-value list.
 */
public class McpObservationContext extends Observation.Context {

  private final String handlerKind;
  private final String handlerName;

  public McpObservationContext(String handlerKind, String handlerName) {
    this.handlerKind = handlerKind;
    this.handlerName = handlerName;
  }

  public String handlerKind() {
    return handlerKind;
  }

  public String handlerName() {
    return handlerName;
  }
}
