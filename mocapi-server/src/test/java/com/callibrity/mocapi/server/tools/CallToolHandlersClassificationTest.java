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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drives every return-type-classification rule that {@link CallToolHandlers#build} enforces. Each
 * nested class covers one axis of classification behavior. The test beans live at the bottom of the
 * file and each have exactly one method named {@code m()} with a specific return signature — the
 * test grabs that method via {@link #methodOf(Class)} and runs it through {@code build}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CallToolHandlersClassificationTest {

  private static final String AWAIT_INTERCEPTOR_DESCRIPTION =
      "Awaits the tool's CompletionStage return value";

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  private CallToolHandler build(Class<?> beanClass) {
    Object bean = instantiate(beanClass);
    Method method = methodOf(beanClass);
    return CallToolHandlers.build(bean, method, generator, mapper, List.of(), s -> s, false);
  }

  private boolean hasAwaitInterceptor(CallToolHandler handler) {
    return handler.describe().interceptors().contains(AWAIT_INTERCEPTOR_DESCRIPTION);
  }

  @Nested
  class When_effective_type_is_void {

    @Test
    void primitive_void_picks_void_mapper_and_advertises_no_schema() {
      var handler = build(PrimitiveVoidBean.class);
      assertThat(handler.resultMapper()).isSameAs(VoidResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(hasAwaitInterceptor(handler)).isFalse();
    }

    @Test
    void boxed_Void_picks_void_mapper_and_advertises_no_schema() {
      var handler = build(BoxedVoidBean.class);
      assertThat(handler.resultMapper()).isSameAs(VoidResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(hasAwaitInterceptor(handler)).isFalse();
    }
  }

  @Nested
  class When_effective_type_is_CallToolResult {

    @Test
    void picks_passthrough_mapper_and_advertises_no_schema() {
      var handler = build(CallToolResultBean.class);
      assertThat(handler.resultMapper()).isSameAs(PassthroughResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(hasAwaitInterceptor(handler)).isFalse();
    }
  }

  @Nested
  class When_effective_type_is_CharSequence {

    @Test
    void plain_String_return_is_accepted_as_text_mapper() {
      var handler = build(StringBean.class);
      assertThat(handler.resultMapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(hasAwaitInterceptor(handler)).isFalse();
    }

    @Test
    void StringBuilder_return_is_accepted_as_text_mapper() {
      var handler = build(StringBuilderBean.class);
      assertThat(handler.resultMapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }

    @Test
    void raw_CharSequence_return_is_accepted_as_text_mapper() {
      var handler = build(CharSequenceBean.class);
      assertThat(handler.resultMapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }
  }

  @Nested
  class When_effective_type_is_a_structured_record {

    @Test
    void picks_structured_mapper_and_advertises_a_derived_object_schema() {
      var handler = build(RecordBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      var schema = handler.descriptor().outputSchema();
      assertThat(schema).isNotNull();
      assertThat(schema.get("type").asString()).isEqualTo("object");
      assertThat(schema.get("properties")).isNotNull();
      assertThat(hasAwaitInterceptor(handler)).isFalse();
    }

    @Test
    void advertised_schema_contains_all_fields_of_the_record() {
      var handler = build(RecordBean.class);
      var props = (ObjectNode) handler.descriptor().outputSchema().get("properties");
      assertThat(props.propertyNames()).contains("name", "age");
    }
  }

  @Nested
  class When_effective_type_is_rejected {

    @Test
    void Object_return_is_rejected() {
      // Jackson emits an empty schema {} for Object — the rejection comes from the "not
      // type:object" branch with type literal "(none)" in the message.
      assertThatThrownBy(() -> build(ObjectBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"(none)\"");
    }

    @Test
    void primitive_int_return_is_rejected_for_yielding_an_integer_schema() {
      assertThatThrownBy(() -> build(IntBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"integer\"");
    }

    @Test
    void primitive_double_return_is_rejected_for_yielding_a_number_schema() {
      assertThatThrownBy(() -> build(DoubleBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"number\"");
    }

    @Test
    void boxed_Boolean_return_is_rejected_for_yielding_a_boolean_schema() {
      assertThatThrownBy(() -> build(BoxedBooleanBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"boolean\"");
    }

    @Test
    void List_return_is_rejected_for_yielding_an_array_schema() {
      assertThatThrownBy(() -> build(ListBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"array\"");
    }

    @Test
    void array_return_is_rejected_for_yielding_an_array_schema() {
      assertThatThrownBy(() -> build(ArrayBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"array\"");
    }

    @Test
    void Map_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> build(MapBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void JsonNode_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> build(JsonNodeBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void ObjectNode_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> build(ObjectNodeBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void Optional_return_is_rejected() {
      assertThatThrownBy(() -> build(OptionalBean.class))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_record_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> build(EmptyRecordBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void rejection_message_names_the_offending_class() {
      assertThatThrownBy(() -> build(ListBean.class))
          .hasMessageContaining(ListBean.class.getName())
          .hasMessageContaining("m");
    }
  }

  @Nested
  class When_return_type_is_async {

    @Test
    void CompletionStage_of_record_unwraps_to_structured_mapper_and_installs_await_interceptor() {
      var handler = build(CompletionStageOfRecordBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema()).isNotNull();
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void CompletableFuture_of_record_unwraps_the_same_way_as_CompletionStage() {
      var handler = build(CompletableFutureOfRecordBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema()).isNotNull();
    }

    @Test
    void CompletionStage_of_Void_unwraps_to_void_mapper_and_installs_await_interceptor() {
      var handler = build(CompletionStageOfVoidBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isSameAs(VoidResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }

    @Test
    void CompletionStage_of_CallToolResult_unwraps_to_passthrough_mapper() {
      var handler = build(CompletionStageOfCallToolResultBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isSameAs(PassthroughResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }

    @Test
    void CompletionStage_of_String_unwraps_to_text_mapper() {
      var handler = build(CompletionStageOfStringBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }

    @Test
    void CompletionStage_of_List_is_rejected_for_yielding_an_array_schema_after_unwrap() {
      assertThatThrownBy(() -> build(CompletionStageOfListBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"array\"");
    }

    @Test
    void raw_CompletionStage_is_rejected_via_findResultType() {
      // The Java compiler refuses to let us declare a raw CompletionStage return type without a
      // @SuppressWarnings("rawtypes") annotation — which the project bans. Instead we mock a
      // Method whose getGenericReturnType() returns CompletionStage.class directly (a Class,
      // not a ParameterizedType), exercising the "no concrete type argument" rejection branch
      // in findResultType.
      Method rawMethod = mock(Method.class);
      when(rawMethod.getGenericReturnType()).thenReturn(CompletionStage.class);
      when(rawMethod.getName()).thenReturn("m");
      Object bean = new CompletionStageOfRecordBean();

      assertThatThrownBy(() -> CallToolHandlers.findResultType(bean, rawMethod))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no concrete type argument");
    }

    @Test
    void wildcard_CompletionStage_is_rejected() {
      assertThatThrownBy(() -> build(WildcardCompletionStageBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no concrete type argument");
    }

    @Test
    void unresolved_type_variable_CompletionStage_is_rejected() {
      // `public <T> CompletionStage<T> m()` has a TypeVariable inner type — no concrete class to
      // derive a schema from, so it's rejected under the same "non-concrete" umbrella.
      assertThatThrownBy(() -> build(TypeVariableCompletionStageBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no concrete type argument");
    }

    @Test
    void nested_CompletionStage_unwraps_recursively_to_the_inner_concrete_type() {
      // The classifier peels CompletionStage layers until a non-stage type appears, so
      // CompletionStage<CompletionStage<Person>> classifies as a structured Person and installs
      // the await interceptor. The interceptor itself loops at runtime to peel both layers.
      var handler = build(NestedCompletionStageBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void CompletableFuture_wrapping_CompletionStage_unwraps_the_same_way() {
      var handler = build(FutureOfStageBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
    }
  }

  // --- helpers ------------------------------------------------------------

  private static Method methodOf(Class<?> beanClass) {
    try {
      return beanClass.getMethod("m");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(
          "Test bean " + beanClass.getSimpleName() + " must declare a public no-arg method m()", e);
    }
  }

  private static Object instantiate(Class<?> beanClass) {
    try {
      return beanClass.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(
          "Test bean " + beanClass.getSimpleName() + " must have a public no-arg constructor", e);
    }
  }

  // --- test beans --------------------------------------------------------

  record Person(String name, int age) {}

  record Nothing() {}

  public static class PrimitiveVoidBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public void m() {
      // Intentionally empty: exercises the classifier's `void` branch.
    }
  }

  public static class BoxedVoidBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Void m() {
      return null;
    }
  }

  public static class CallToolResultBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CallToolResult m() {
      return null;
    }
  }

  public static class StringBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public String m() {
      return "hi";
    }
  }

  public static class StringBuilderBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public StringBuilder m() {
      return new StringBuilder("hi");
    }
  }

  public static class CharSequenceBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CharSequence m() {
      return "hi";
    }
  }

  public static class RecordBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Person m() {
      return new Person("Ada", 36);
    }
  }

  public static class ObjectBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Object m() {
      return null;
    }
  }

  public static class IntBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public int m() {
      return 1;
    }
  }

  public static class DoubleBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public double m() {
      return 1.0;
    }
  }

  public static class BoxedBooleanBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Boolean m() {
      return Boolean.TRUE;
    }
  }

  public static class ListBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public List<Person> m() {
      return List.of();
    }
  }

  public static class ArrayBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Person[] m() {
      return new Person[0];
    }
  }

  public static class MapBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Map<String, Person> m() {
      return Map.of();
    }
  }

  public static class JsonNodeBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public JsonNode m() {
      return null;
    }
  }

  public static class ObjectNodeBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public ObjectNode m() {
      return null;
    }
  }

  public static class OptionalBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Optional<Person> m() {
      return Optional.empty();
    }
  }

  public static class EmptyRecordBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public Nothing m() {
      return new Nothing();
    }
  }

  public static class CompletionStageOfRecordBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<Person> m() {
      return CompletableFuture.completedFuture(new Person("Ada", 36));
    }
  }

  public static class CompletableFutureOfRecordBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletableFuture<Person> m() {
      return CompletableFuture.completedFuture(new Person("Ada", 36));
    }
  }

  public static class CompletionStageOfVoidBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<Void> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class CompletionStageOfCallToolResultBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<CallToolResult> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class CompletionStageOfStringBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<String> m() {
      return CompletableFuture.completedFuture("hi");
    }
  }

  public static class CompletionStageOfListBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<List<Person>> m() {
      return CompletableFuture.completedFuture(List.of());
    }
  }

  public static class WildcardCompletionStageBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<?> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class TypeVariableCompletionStageBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public <T> CompletionStage<T> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class NestedCompletionStageBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletionStage<CompletionStage<Person>> m() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Person("Ada", 36)));
    }
  }

  public static class FutureOfStageBean {
    @com.callibrity.mocapi.api.tools.McpTool
    public CompletableFuture<CompletionStage<Person>> m() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Person("Ada", 36)));
    }
  }
}
