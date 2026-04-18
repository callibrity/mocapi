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
package com.callibrity.mocapi.server.completions;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts completion candidate values from annotated method parameters. Supports Java enum types
 * (uses {@link Enum#name()}) and {@link Schema#allowableValues()} on non-enum types. When both are
 * present on the same parameter, the enum wins because its set is guaranteed to match what the
 * runtime conversion service will accept.
 */
public final class CompletionCandidates {

  private CompletionCandidates() {}

  public static List<String> valuesFor(Parameter parameter) {
    Class<?> type = parameter.getType();
    if (type.isEnum()) {
      return Arrays.stream(type.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList();
    }
    Schema schema = parameter.getAnnotation(Schema.class);
    if (schema != null && schema.allowableValues().length > 0) {
      return List.of(schema.allowableValues());
    }
    return List.of();
  }
}
