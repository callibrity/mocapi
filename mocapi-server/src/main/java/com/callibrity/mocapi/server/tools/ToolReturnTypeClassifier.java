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
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.reflect.TypeUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Classifies a tool method's declared return type into one of three permitted shapes, rejecting
 * everything else with a type-specific message at handler-build time (so misconfigured tools fail
 * at startup, not with a silent wire mismatch).
 *
 * <p>Permitted shapes, applied to the <em>effective</em> return type (after optionally stripping
 * one layer of {@link CompletionStage}):
 *
 * <ol>
 *   <li>{@code void} / {@link Void} — no output schema, {@link VoidResultMapper}
 *   <li>{@link CallToolResult} — author constructs the result manually; no output schema, {@link
 *       PassthroughResultMapper}
 *   <li>{@link CharSequence} (typically {@link String}) — ergonomic shortcut for text-only tool
 *       results; no output schema, {@link TextContentResultMapper}
 *   <li>A POJO/record whose Jackson-derived schema has {@code type: "object"} with at least one
 *       declared property — schema is advertised, {@link StructuredResultMapper}
 * </ol>
 *
 * <p>Everything else is rejected. The rule set intentionally uses a semantic check on the generated
 * schema (rather than a hand-maintained class blocklist) so new "object-ish but empty" types (e.g.,
 * a future library's {@code Map} analog) are caught automatically.
 *
 * <p>Async support is one layer deep: {@code CompletionStage<X>} (covering {@link
 * java.util.concurrent.CompletableFuture} since it implements {@code CompletionStage}) unwraps to
 * {@code X}, which must itself be one of the three permitted shapes. Raw, wildcard, and nested
 * {@code CompletionStage} are rejected.
 */
final class ToolReturnTypeClassifier {

  private static final String RETURN_TYPE_PREFIX = "return type ";

  private static final String NESTED_COMPLETION_STAGE =
      "nested CompletionStage (%s). A tool must await at most one layer of asynchrony; "
          + "flatten the inner stage before returning.";

  private static final String RAW_COMPLETION_STAGE =
      "raw %s (no type argument). "
          + "Declare a concrete type argument such as CompletionStage<MyRecord>.";

  private static final String WILDCARD_COMPLETION_STAGE =
      "CompletionStage<?> wildcard (%s). Declare a concrete type argument.";

  private static final String TYPE_VARIABLE_COMPLETION_STAGE =
      "CompletionStage with an unresolved type variable (%s). Declare a concrete type argument.";

  private static final String NON_OBJECT_SCHEMA =
      RETURN_TYPE_PREFIX
          + "%s produces a JSON schema of type \"%s\"; MCP requires structuredContent to be a "
          + "JSON object. Wrap the value in a record whose fields describe the payload, or return "
          + "CallToolResult to construct the result manually.";

  private static final String EMPTY_PROPERTIES_SCHEMA =
      RETURN_TYPE_PREFIX
          + "%s produces an object schema with no declared properties (%s). MCP requires "
          + "structuredContent to be a JSON object with a known shape. Use a concrete record/class "
          + "whose fields describe the payload, or return CallToolResult to construct the result "
          + "manually.";

  private static final String REJECTION_WRAPPER = "@McpTool %s.%s: %s";

  private ToolReturnTypeClassifier() {}

  static Classification classify(
      Object bean, Method method, MethodSchemaGenerator generator, ObjectMapper objectMapper) {
    Class<?> beanClass = bean.getClass();
    Type genericReturnType = method.getGenericReturnType();
    // TypeUtils.getRawType can't return null here — a reflected Method always has a resolvable
    // generic return type, so we don't bother with a defensive null check. Same for innerRawType
    // below. If that invariant ever breaks, the subsequent isAssignableFrom check will NPE and
    // loudly tell us, which is a better failure mode than silently misclassifying.
    Class<?> rawReturnType = TypeUtils.getRawType(genericReturnType, beanClass);

    boolean async = false;
    Type effectiveGenericType = genericReturnType;
    Class<?> effectiveRawType = rawReturnType;

    if (CompletionStage.class.isAssignableFrom(rawReturnType)) {
      async = true;
      Type innerGenericType = unwrapCompletionStage(bean, method, genericReturnType);
      Class<?> innerRawType = TypeUtils.getRawType(innerGenericType, beanClass);
      if (CompletionStage.class.isAssignableFrom(innerRawType)) {
        throw rejection(
            bean, method, String.format(NESTED_COMPLETION_STAGE, genericReturnType.getTypeName()));
      }
      effectiveGenericType = innerGenericType;
      effectiveRawType = innerRawType;
    }

    if (effectiveRawType == void.class || effectiveRawType == Void.class) {
      return new Classification(VoidResultMapper.INSTANCE, null, async);
    }
    if (effectiveRawType == CallToolResult.class) {
      return new Classification(PassthroughResultMapper.INSTANCE, null, async);
    }
    if (CharSequence.class.isAssignableFrom(effectiveRawType)) {
      return new Classification(TextContentResultMapper.INSTANCE, null, async);
    }

    ObjectNode outputSchema = generator.generateSchema(effectiveRawType);
    requireObjectShapedSchema(bean, method, effectiveGenericType, outputSchema);
    return new Classification(new StructuredResultMapper(objectMapper), outputSchema, async);
  }

  private static Type unwrapCompletionStage(Object bean, Method method, Type genericReturnType) {
    if (!(genericReturnType instanceof ParameterizedType paramType)) {
      throw rejection(
          bean, method, String.format(RAW_COMPLETION_STAGE, genericReturnType.getTypeName()));
    }
    // CompletionStage<T> has exactly one type parameter by its source declaration, so
    // getActualTypeArguments() always returns length 1 — no arity check needed.
    Type innerType = paramType.getActualTypeArguments()[0];
    if (innerType instanceof WildcardType) {
      throw rejection(
          bean, method, String.format(WILDCARD_COMPLETION_STAGE, genericReturnType.getTypeName()));
    }
    if (innerType instanceof TypeVariable<?>) {
      throw rejection(
          bean,
          method,
          String.format(TYPE_VARIABLE_COMPLETION_STAGE, genericReturnType.getTypeName()));
    }
    return innerType;
  }

  private static void requireObjectShapedSchema(
      Object bean, Method method, Type effectiveType, ObjectNode schema) {
    JsonNode typeNode = schema.get("type");
    String actualType = typeNode == null ? "(none)" : typeNode.asString();
    if (!"object".equals(actualType)) {
      throw rejection(
          bean, method, String.format(NON_OBJECT_SCHEMA, effectiveType.getTypeName(), actualType));
    }
    // Jackson's schema generator either emits a populated `properties` object or omits the field
    // entirely — it never emits `"properties": {}`. So a null check is sufficient; an additional
    // `isEmpty()` check would be dead code under the current generator.
    if (schema.get("properties") == null) {
      throw rejection(
          bean,
          method,
          String.format(EMPTY_PROPERTIES_SCHEMA, effectiveType.getTypeName(), schema));
    }
  }

  private static IllegalArgumentException rejection(Object bean, Method method, String reason) {
    return new IllegalArgumentException(
        String.format(REJECTION_WRAPPER, bean.getClass().getName(), method.getName(), reason));
  }

  /**
   * The classification outcome for a tool method's return type. {@code outputSchema} is {@code
   * null} when the declared shape has no advertisable schema ({@code void}, {@link
   * CallToolResult}); {@code async} is {@code true} when the raw return type is a {@link
   * CompletionStage}, signalling that the await interceptor should be installed innermost.
   */
  record Classification(ResultMapper mapper, ObjectNode outputSchema, boolean async) {}
}
