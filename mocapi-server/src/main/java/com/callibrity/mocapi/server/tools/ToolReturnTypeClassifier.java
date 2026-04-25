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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.reflect.TypeUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Classifies a tool method's declared return type into one of four permitted shapes, rejecting
 * anything else at handler-build time.
 *
 * <p>Permitted shapes (applied to the effective type after peeling off any number of {@code
 * CompletionStage} layers):
 *
 * <ol>
 *   <li>{@code void} / {@link Void} — {@link VoidResultMapper}, no output schema
 *   <li>{@link CallToolResult} — {@link PassthroughResultMapper}, no output schema
 *   <li>{@link CharSequence} — {@link TextContentResultMapper}, no output schema
 *   <li>A POJO/record whose Jackson schema is {@code "type": "object"} with declared properties —
 *       {@link StructuredResultMapper}, schema advertised
 * </ol>
 *
 * <p>{@code CompletionStage<X>} (and {@code CompletableFuture<X>}) is unwrapped recursively; each
 * layer gets one {@link CompletionStageAwaitingInterceptor} at runtime. The inner type argument
 * must be a concrete class or parameterized type — wildcards and unresolved type variables are
 * rejected because there's no shape to derive a schema from.
 */
final class ToolReturnTypeClassifier {

  private ToolReturnTypeClassifier() {}

  static Classification classify(
      Object bean, Method method, MethodSchemaGenerator generator, ObjectMapper objectMapper) {
    Class<?> beanClass = bean.getClass();
    Type type = method.getGenericReturnType();
    Class<?> rawType = TypeUtils.getRawType(type, beanClass);
    int asyncDepth = 0;

    // Peel CompletionStage layers; each layer becomes one await interceptor at runtime.
    while (CompletionStage.class.isAssignableFrom(rawType)) {
      if (!(type instanceof ParameterizedType p) || !isConcrete(p.getActualTypeArguments()[0])) {
        throw rejection(
            bean,
            method,
            "CompletionStage with no concrete type argument ("
                + type.getTypeName()
                + "). Declare a concrete inner type, e.g. CompletionStage<MyRecord>.");
      }
      type = p.getActualTypeArguments()[0];
      rawType = TypeUtils.getRawType(type, beanClass);
      asyncDepth++;
    }

    if (rawType == void.class || rawType == Void.class) {
      return new Classification(VoidResultMapper.INSTANCE, null, asyncDepth);
    }
    if (rawType == CallToolResult.class) {
      return new Classification(PassthroughResultMapper.INSTANCE, null, asyncDepth);
    }
    if (CharSequence.class.isAssignableFrom(rawType)) {
      return new Classification(TextContentResultMapper.INSTANCE, null, asyncDepth);
    }

    ObjectNode schema = generator.generateSchema(rawType);
    String schemaType = schema.get("type") == null ? "(none)" : schema.get("type").asString();
    if (!"object".equals(schemaType)) {
      throw rejection(
          bean,
          method,
          "return type "
              + type.getTypeName()
              + " produces a JSON schema of type \""
              + schemaType
              + "\"; structuredContent must be a JSON object. Wrap the value in a record/POJO, "
              + "or return CallToolResult to build the result manually.");
    }
    if (schema.get("properties") == null) {
      throw rejection(
          bean,
          method,
          "return type "
              + type.getTypeName()
              + " produces an object schema with no declared properties ("
              + schema
              + "). Use a concrete record/class with named fields, or return CallToolResult.");
    }
    return new Classification(new StructuredResultMapper(objectMapper), schema, asyncDepth);
  }

  /**
   * A {@link CompletionStage} type argument is "concrete" if it has a derivable schema shape —
   * either a {@link Class} (e.g. {@code CompletionStage<Person>}) or a {@link ParameterizedType}
   * (e.g. {@code CompletionStage<List<Person>>} or {@code CompletionStage<CompletionStage<X>>}).
   * Wildcards, type variables, and generic array types have no shape to inspect and are rejected.
   */
  private static boolean isConcrete(Type t) {
    return t instanceof Class<?> || t instanceof ParameterizedType;
  }

  private static IllegalArgumentException rejection(Object bean, Method method, String reason) {
    return new IllegalArgumentException(
        "@McpTool " + bean.getClass().getName() + "." + method.getName() + ": " + reason);
  }

  /**
   * Outcome of classifying a tool's return type. {@code outputSchema} is {@code null} for shapes
   * with no advertisable schema (void, {@link CallToolResult}, {@link CharSequence}). {@code
   * asyncDepth} is the number of {@link CompletionStage} layers wrapping the effective type — 0 for
   * synchronous, 1 for {@code CompletionStage<X>}, 2 for {@code
   * CompletionStage<CompletionStage<X>>}, etc. The handler builder installs one await interceptor
   * per layer.
   */
  record Classification(ResultMapper mapper, ObjectNode outputSchema, int asyncDepth) {}
}
