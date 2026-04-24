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
 * End-to-end exercise of {@link ToolReturnTypeClassifier}. Each nested class covers one axis of
 * classification behavior. The test beans live at the bottom of the file and each have exactly one
 * method named {@code m()} with a specific return signature — the test grabs that method via {@link
 * #methodOf(Class)} and runs it through the classifier.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolReturnTypeClassifierTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  private ToolReturnTypeClassifier.Classification classify(Class<?> beanClass) {
    Object bean = instantiate(beanClass);
    Method method = methodOf(beanClass);
    return ToolReturnTypeClassifier.classify(bean, method, generator, mapper);
  }

  @Nested
  class When_effective_type_is_void {

    @Test
    void primitive_void_returns_void_mapper_and_no_schema() {
      var c = classify(PrimitiveVoidBean.class);
      assertThat(c.mapper()).isSameAs(VoidResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
      assertThat(c.async()).isFalse();
    }

    @Test
    void boxed_Void_returns_void_mapper_and_no_schema() {
      var c = classify(BoxedVoidBean.class);
      assertThat(c.mapper()).isSameAs(VoidResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
      assertThat(c.async()).isFalse();
    }
  }

  @Nested
  class When_effective_type_is_CallToolResult {

    @Test
    void returns_passthrough_mapper_and_no_schema() {
      var c = classify(CallToolResultBean.class);
      assertThat(c.mapper()).isSameAs(PassthroughResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
      assertThat(c.async()).isFalse();
    }
  }

  @Nested
  class When_effective_type_is_CharSequence {

    @Test
    void plain_String_return_is_accepted_as_text_mapper() {
      var c = classify(StringBean.class);
      assertThat(c.mapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
      assertThat(c.async()).isFalse();
    }

    @Test
    void StringBuilder_return_is_accepted_as_text_mapper() {
      var c = classify(StringBuilderBean.class);
      assertThat(c.mapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
    }

    @Test
    void raw_CharSequence_return_is_accepted_as_text_mapper() {
      var c = classify(CharSequenceBean.class);
      assertThat(c.mapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
    }
  }

  @Nested
  class When_effective_type_is_a_structured_record {

    @Test
    void returns_structured_mapper_and_derived_object_schema() {
      var c = classify(RecordBean.class);
      assertThat(c.mapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(c.outputSchema()).isNotNull();
      assertThat(c.outputSchema().get("type").asString()).isEqualTo("object");
      assertThat(c.outputSchema().get("properties")).isNotNull();
      assertThat(c.async()).isFalse();
    }

    @Test
    void advertised_schema_contains_all_fields_of_the_record() {
      var c = classify(RecordBean.class);
      ObjectNode props = (ObjectNode) c.outputSchema().get("properties");
      assertThat(props.propertyNames()).contains("name", "age");
    }
  }

  @Nested
  class When_effective_type_is_rejected {

    @Test
    void Object_return_is_rejected() {
      // Jackson emits an empty schema {} for Object (no `type` field), so the rejection comes
      // from the "not type:object" branch with type literal "(none)" in the message.
      assertThatThrownBy(() -> classify(ObjectBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"(none)\"");
    }

    @Test
    void primitive_int_return_is_rejected() {
      assertThatThrownBy(() -> classify(IntBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"integer\"");
    }

    @Test
    void primitive_double_return_is_rejected() {
      assertThatThrownBy(() -> classify(DoubleBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"number\"");
    }

    @Test
    void boxed_Boolean_return_is_rejected() {
      assertThatThrownBy(() -> classify(BoxedBooleanBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"boolean\"");
    }

    @Test
    void List_return_is_rejected_as_array_schema() {
      assertThatThrownBy(() -> classify(ListBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"array\"");
    }

    @Test
    void array_return_is_rejected_as_array_schema() {
      assertThatThrownBy(() -> classify(ArrayBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"array\"");
    }

    @Test
    void Map_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> classify(MapBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void JsonNode_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> classify(JsonNodeBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void ObjectNode_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> classify(ObjectNodeBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void Optional_return_is_rejected() {
      assertThatThrownBy(() -> classify(OptionalBean.class))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_record_return_is_rejected_for_having_no_declared_properties() {
      assertThatThrownBy(() -> classify(EmptyRecordBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("object schema with no declared properties");
    }

    @Test
    void rejection_message_names_the_offending_class() {
      assertThatThrownBy(() -> classify(ListBean.class))
          .hasMessageContaining(ListBean.class.getName())
          .hasMessageContaining("m");
    }
  }

  @Nested
  class When_return_type_is_async {

    @Test
    void CompletionStage_of_record_unwraps_to_structured_mapper_with_schema() {
      var c = classify(CompletionStageOfRecordBean.class);
      assertThat(c.async()).isTrue();
      assertThat(c.mapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(c.outputSchema()).isNotNull();
      assertThat(c.outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void CompletableFuture_of_record_unwraps_to_structured_mapper_with_schema() {
      var c = classify(CompletableFutureOfRecordBean.class);
      assertThat(c.async()).isTrue();
      assertThat(c.mapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(c.outputSchema()).isNotNull();
    }

    @Test
    void CompletionStage_of_Void_unwraps_to_void_mapper_with_no_schema() {
      var c = classify(CompletionStageOfVoidBean.class);
      assertThat(c.async()).isTrue();
      assertThat(c.mapper()).isSameAs(VoidResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
    }

    @Test
    void CompletionStage_of_CallToolResult_unwraps_to_passthrough_mapper_with_no_schema() {
      var c = classify(CompletionStageOfCallToolResultBean.class);
      assertThat(c.async()).isTrue();
      assertThat(c.mapper()).isSameAs(PassthroughResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
    }

    @Test
    void CompletionStage_of_String_unwraps_to_text_mapper_with_no_schema() {
      var c = classify(CompletionStageOfStringBean.class);
      assertThat(c.async()).isTrue();
      assertThat(c.mapper()).isSameAs(TextContentResultMapper.INSTANCE);
      assertThat(c.outputSchema()).isNull();
    }

    @Test
    void CompletionStage_of_List_is_rejected_with_array_schema_message() {
      assertThatThrownBy(() -> classify(CompletionStageOfListBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("\"array\"");
    }

    @Test
    void raw_CompletionStage_is_rejected() throws NoSuchMethodException {
      // The Java compiler refuses to let us declare a raw CompletionStage return type without a
      // @SuppressWarnings("rawtypes") annotation — which the project bans. Instead we mock a
      // Method whose getGenericReturnType() returns CompletionStage.class directly (a Class, not
      // a ParameterizedType), exercising the "raw" rejection branch in unwrapCompletionStage.
      Method rawMethod = mock(Method.class);
      when(rawMethod.getGenericReturnType()).thenReturn(CompletionStage.class);
      when(rawMethod.getName()).thenReturn("m");
      Object bean = new CompletionStageOfRecordBean();

      assertThatThrownBy(
              () -> ToolReturnTypeClassifier.classify(bean, rawMethod, generator, mapper))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("raw")
          .hasMessageContaining("no type argument");
    }

    @Test
    void wildcard_CompletionStage_is_rejected() {
      assertThatThrownBy(() -> classify(WildcardCompletionStageBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("wildcard");
    }

    @Test
    void unresolved_type_variable_CompletionStage_is_rejected() {
      // A generic method like `public <T> CompletionStage<T> m()` has a TypeVariable inner type —
      // there's no concrete class to derive a schema from, so classify must reject it with a
      // dedicated message rather than misfiring later in the schema generator.
      assertThatThrownBy(() -> classify(TypeVariableCompletionStageBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("type variable");
    }

    @Test
    void nested_CompletionStage_is_rejected() {
      assertThatThrownBy(() -> classify(NestedCompletionStageBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("nested CompletionStage");
    }

    @Test
    void CompletableFuture_wrapping_CompletionStage_is_also_rejected_as_nested() {
      assertThatThrownBy(() -> classify(FutureOfStageBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("nested CompletionStage");
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
    public void m() {
      // Intentionally empty: exercises the classifier's `void` return-type branch, which
      // doesn't care about the body.
    }
  }

  public static class BoxedVoidBean {
    public Void m() {
      return null;
    }
  }

  public static class CallToolResultBean {
    public CallToolResult m() {
      return null;
    }
  }

  public static class StringBean {
    public String m() {
      return "hi";
    }
  }

  public static class StringBuilderBean {
    public StringBuilder m() {
      return new StringBuilder("hi");
    }
  }

  public static class CharSequenceBean {
    public CharSequence m() {
      return "hi";
    }
  }

  public static class RecordBean {
    public Person m() {
      return new Person("Ada", 36);
    }
  }

  public static class ObjectBean {
    public Object m() {
      return null;
    }
  }

  public static class IntBean {
    public int m() {
      return 1;
    }
  }

  public static class DoubleBean {
    public double m() {
      return 1.0;
    }
  }

  public static class BoxedBooleanBean {
    public Boolean m() {
      return Boolean.TRUE;
    }
  }

  public static class ListBean {
    public List<Person> m() {
      return List.of();
    }
  }

  public static class ArrayBean {
    public Person[] m() {
      return new Person[0];
    }
  }

  public static class MapBean {
    public Map<String, Person> m() {
      return Map.of();
    }
  }

  public static class JsonNodeBean {
    public JsonNode m() {
      return null;
    }
  }

  public static class ObjectNodeBean {
    public ObjectNode m() {
      return null;
    }
  }

  public static class OptionalBean {
    public Optional<Person> m() {
      return Optional.empty();
    }
  }

  public static class EmptyRecordBean {
    public Nothing m() {
      return new Nothing();
    }
  }

  public static class CompletionStageOfRecordBean {
    public CompletionStage<Person> m() {
      return CompletableFuture.completedFuture(new Person("Ada", 36));
    }
  }

  public static class CompletableFutureOfRecordBean {
    public CompletableFuture<Person> m() {
      return CompletableFuture.completedFuture(new Person("Ada", 36));
    }
  }

  public static class CompletionStageOfVoidBean {
    public CompletionStage<Void> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class CompletionStageOfCallToolResultBean {
    public CompletionStage<CallToolResult> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class CompletionStageOfStringBean {
    public CompletionStage<String> m() {
      return CompletableFuture.completedFuture("hi");
    }
  }

  public static class CompletionStageOfListBean {
    public CompletionStage<List<Person>> m() {
      return CompletableFuture.completedFuture(List.of());
    }
  }

  public static class WildcardCompletionStageBean {
    public CompletionStage<?> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class TypeVariableCompletionStageBean {
    public <T> CompletionStage<T> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class NestedCompletionStageBean {
    public CompletionStage<CompletionStage<Person>> m() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Person("Ada", 36)));
    }
  }

  public static class FutureOfStageBean {
    public CompletableFuture<CompletionStage<Person>> m() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Person("Ada", 36)));
    }
  }
}
