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

import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.RequestedSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An MCP elicitation schema. Wraps the property definitions and required field names, producing a
 * typed {@link RequestedSchema}.
 */
public final class ElicitationSchema {

  private final Map<String, PrimitiveSchemaDefinition> properties;
  private final List<String> requiredNames;

  ElicitationSchema(Map<String, PrimitiveSchemaDefinition> properties, List<String> requiredNames) {
    this.properties = properties;
    this.requiredNames = requiredNames;
  }

  /** Creates a new builder for constructing an {@link ElicitationSchema}. */
  public static ElicitationSchemaBuilder builder() {
    return new ElicitationSchemaBuilder();
  }

  /**
   * Produces a typed {@link RequestedSchema} from this elicitation schema.
   *
   * @return a RequestedSchema with properties and required field names
   */
  public RequestedSchema toRequestedSchema() {
    List<String> required = requiredNames.isEmpty() ? null : List.copyOf(requiredNames);
    return new RequestedSchema(new LinkedHashMap<>(properties), required);
  }
}
