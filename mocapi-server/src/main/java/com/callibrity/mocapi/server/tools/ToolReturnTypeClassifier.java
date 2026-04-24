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

  private ToolReturnTypeClassifier() {}

  static Classification classify(
      Object bean, Method method, MethodSchemaGenerator generator, ObjectMapper objectMapper) {
    Class<?> beanClass = bean.getClass();
    Type genericReturnType = method.getGenericReturnType();
    Class<?> rawReturnType = TypeUtils.getRawType(genericReturnType, beanClass);
    if (rawReturnType == null) {
      throw rejection(
          bean,
          method,
          "return type "
              + genericReturnType.getTypeName()
              + " could not be resolved to a concrete class.");
    }

    boolean async = false;
    Type effectiveGenericType = genericReturnType;
    Class<?> effectiveRawType = rawReturnType;

    if (CompletionStage.class.isAssignableFrom(rawReturnType)) {
      async = true;
      Type innerGenericType = unwrapCompletionStage(bean, method, genericReturnType);
      Class<?> innerRawType = TypeUtils.getRawType(innerGenericType, beanClass);
      if (innerRawType == null) {
        throw rejection(
            bean,
            method,
            "CompletionStage inner type "
                + innerGenericType.getTypeName()
                + " could not be resolved to a concrete class.");
      }
      if (CompletionStage.class.isAssignableFrom(innerRawType)) {
        throw rejection(
            bean,
            method,
            "nested CompletionStage ("
                + genericReturnType.getTypeName()
                + "). A tool must await at most one layer of asynchrony; "
                + "flatten the inner stage before returning.");
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
          bean,
          method,
          "raw "
              + genericReturnType.getTypeName()
              + " (no type argument). "
              + "Declare a concrete type argument such as CompletionStage<MyRecord>.");
    }
    Type[] args = paramType.getActualTypeArguments();
    if (args.length != 1) {
      throw rejection(
          bean,
          method,
          "CompletionStage declared with "
              + args.length
              + " type arguments; expected exactly one.");
    }
    Type innerType = args[0];
    if (innerType instanceof WildcardType) {
      throw rejection(
          bean,
          method,
          "CompletionStage<?> wildcard ("
              + genericReturnType.getTypeName()
              + "). Declare a concrete type argument.");
    }
    if (innerType instanceof TypeVariable<?>) {
      throw rejection(
          bean,
          method,
          "CompletionStage with an unresolved type variable ("
              + genericReturnType.getTypeName()
              + "). Declare a concrete type argument.");
    }
    return innerType;
  }

  private static void requireObjectShapedSchema(
      Object bean, Method method, Type effectiveType, ObjectNode schema) {
    JsonNode typeNode = schema.get("type");
    String actualType = typeNode == null ? "(none)" : typeNode.asString();
    if (!"object".equals(actualType)) {
      throw rejection(
          bean,
          method,
          "return type "
              + effectiveType.getTypeName()
              + " produces a JSON schema of type \""
              + actualType
              + "\"; MCP requires structuredContent to be a JSON object. "
              + "Wrap the value in a record whose fields describe the payload, "
              + "or return CallToolResult to construct the result manually.");
    }
    JsonNode props = schema.get("properties");
    if (props == null || props.isEmpty()) {
      throw rejection(
          bean,
          method,
          "return type "
              + effectiveType.getTypeName()
              + " produces an object schema with no declared properties ("
              + schema
              + "). MCP requires structuredContent to be a JSON object with a known shape. "
              + "Use a concrete record/class whose fields describe the payload, "
              + "or return CallToolResult to construct the result manually.");
    }
  }

  private static IllegalArgumentException rejection(Object bean, Method method, String reason) {
    return new IllegalArgumentException(
        "@McpTool " + bean.getClass().getName() + "." + method.getName() + ": " + reason);
  }

  /**
   * The classification outcome for a tool method's return type. {@code outputSchema} is {@code
   * null} when the declared shape has no advertisable schema ({@code void}, {@link
   * CallToolResult}); {@code async} is {@code true} when the raw return type is a {@link
   * CompletionStage}, signalling that the await interceptor should be installed innermost.
   */
  record Classification(ResultMapper mapper, ObjectNode outputSchema, boolean async) {}
}
