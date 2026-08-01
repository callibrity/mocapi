/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ImageContent;
import com.callibrity.mocapi.model.ResourceLink;
import com.callibrity.mocapi.model.TextContent;
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
    return CallToolHandlers.build(
        bean,
        method,
        new CallToolHandlers.BuildParams(generator, mapper, List.of(), List.of(), s -> s, false));
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
  class When_effective_type_is_a_ContentBlock {

    @Test
    void single_ContentBlock_return_picks_the_content_block_mapper_and_advertises_no_schema() {
      var handler = build(ImageContentBean.class);
      assertThat(handler.resultMapper()).isSameAs(ContentBlockResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(hasAwaitInterceptor(handler)).isFalse();
    }

    @Test
    void ResourceLink_return_picks_the_content_block_mapper() {
      var handler = build(ResourceLinkBean.class);
      assertThat(handler.resultMapper()).isSameAs(ContentBlockResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }

    @Test
    void TextContent_return_routes_to_the_content_block_mapper_not_the_text_mapper() {
      // TextContent is a ContentBlock but not a CharSequence, so it becomes a single content block
      // rather than the toString() text shortcut.
      var handler = build(TextContentBean.class);
      assertThat(handler.resultMapper()).isSameAs(ContentBlockResultMapper.INSTANCE);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }
  }

  @Nested
  class When_effective_type_is_structured_non_object {

    // MCP 2026-07-28 widened structuredContent from a JSON object to any JSON value, so non-object
    // return types are accepted and advertise a schema of their derived JSON type.

    @Test
    void primitive_int_return_advertises_an_integer_schema() {
      var handler = build(IntBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("integer");
    }

    @Test
    void primitive_double_return_advertises_a_number_schema() {
      var handler = build(DoubleBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("number");
    }

    @Test
    void boxed_Boolean_return_advertises_a_boolean_schema() {
      var handler = build(BoxedBooleanBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("boolean");
    }

    @Test
    void List_return_advertises_an_array_schema() {
      var handler = build(ListBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("array");
    }

    @Test
    void array_return_advertises_an_array_schema() {
      var handler = build(ArrayBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("array");
    }

    @Test
    void Map_return_advertises_an_object_schema() {
      var handler = build(MapBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void JsonNode_return_advertises_an_object_schema() {
      var handler = build(JsonNodeBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void ObjectNode_return_advertises_an_object_schema() {
      var handler = build(ObjectNodeBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void empty_record_return_advertises_an_object_schema() {
      var handler = build(EmptyRecordBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void Object_return_is_structured_with_no_advertised_schema() {
      // Jackson emits an empty schema {} for Object — there is no meaningful type to advertise, so
      // no outputSchema is attached, but the value is still mapped to structuredContent.
      var handler = build(ObjectBean.class);
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema()).isNull();
    }
  }

  @Nested
  class When_effective_type_cannot_be_mapped {

    @Test
    void Optional_return_is_rejected_because_its_element_type_is_erased() {
      // Optional is a Java container, not a JSON type; its element type is erased on the return
      // signature, so no meaningful structuredContent schema can be derived.
      assertThatThrownBy(() -> build(OptionalBean.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Optional");
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
    void CompletionStage_of_ContentBlock_unwraps_to_content_block_mapper() {
      var handler = build(CompletionStageOfImageContentBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isSameAs(ContentBlockResultMapper.INSTANCE);
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
    void CompletionStage_of_List_unwraps_to_a_structured_array_mapper() {
      var handler = build(CompletionStageOfListBean.class);
      assertThat(hasAwaitInterceptor(handler)).isTrue();
      assertThat(handler.resultMapper()).isInstanceOf(StructuredResultMapper.class);
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("array");
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
    @McpTool
    public void m() {
      // Intentionally empty: exercises the classifier's `void` branch.
    }
  }

  public static class BoxedVoidBean {
    @McpTool
    public Void m() {
      return null;
    }
  }

  public static class CallToolResultBean {
    @McpTool
    public CallToolResult m() {
      return null;
    }
  }

  public static class StringBean {
    @McpTool
    public String m() {
      return "hi";
    }
  }

  public static class ImageContentBean {
    @McpTool
    public ImageContent m() {
      return new ImageContent("data", "image/png", null);
    }
  }

  public static class CompletionStageOfImageContentBean {
    @McpTool
    public CompletionStage<ImageContent> m() {
      return CompletableFuture.completedFuture(new ImageContent("data", "image/png", null));
    }
  }

  public static class ResourceLinkBean {
    @McpTool
    public ResourceLink m() {
      return new ResourceLink("file:///doc", "text/plain", null);
    }
  }

  public static class TextContentBean {
    @McpTool
    public TextContent m() {
      return new TextContent("hi", null);
    }
  }

  public static class StringBuilderBean {
    @McpTool
    public StringBuilder m() {
      return new StringBuilder("hi");
    }
  }

  public static class CharSequenceBean {
    @McpTool
    public CharSequence m() {
      return "hi";
    }
  }

  public static class RecordBean {
    @McpTool
    public Person m() {
      return new Person("Ada", 36);
    }
  }

  public static class ObjectBean {
    @McpTool
    public Object m() {
      return null;
    }
  }

  public static class IntBean {
    @McpTool
    public int m() {
      return 1;
    }
  }

  public static class DoubleBean {
    @McpTool
    public double m() {
      return 1.0;
    }
  }

  public static class BoxedBooleanBean {
    @McpTool
    public Boolean m() {
      return Boolean.TRUE;
    }
  }

  public static class ListBean {
    @McpTool
    public List<Person> m() {
      return List.of();
    }
  }

  public static class ArrayBean {
    @McpTool
    public Person[] m() {
      return new Person[0];
    }
  }

  public static class MapBean {
    @McpTool
    public Map<String, Person> m() {
      return Map.of();
    }
  }

  public static class JsonNodeBean {
    @McpTool
    public JsonNode m() {
      return null;
    }
  }

  public static class ObjectNodeBean {
    @McpTool
    public ObjectNode m() {
      return null;
    }
  }

  public static class OptionalBean {
    @McpTool
    public Optional<Person> m() {
      return Optional.empty();
    }
  }

  public static class EmptyRecordBean {
    @McpTool
    public Nothing m() {
      return new Nothing();
    }
  }

  public static class CompletionStageOfRecordBean {
    @McpTool
    public CompletionStage<Person> m() {
      return CompletableFuture.completedFuture(new Person("Ada", 36));
    }
  }

  public static class CompletableFutureOfRecordBean {
    @McpTool
    public CompletableFuture<Person> m() {
      return CompletableFuture.completedFuture(new Person("Ada", 36));
    }
  }

  public static class CompletionStageOfVoidBean {
    @McpTool
    public CompletionStage<Void> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class CompletionStageOfCallToolResultBean {
    @McpTool
    public CompletionStage<CallToolResult> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class CompletionStageOfStringBean {
    @McpTool
    public CompletionStage<String> m() {
      return CompletableFuture.completedFuture("hi");
    }
  }

  public static class CompletionStageOfListBean {
    @McpTool
    public CompletionStage<List<Person>> m() {
      return CompletableFuture.completedFuture(List.of());
    }
  }

  public static class WildcardCompletionStageBean {
    @McpTool
    public CompletionStage<?> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class TypeVariableCompletionStageBean {
    @McpTool
    public <T> CompletionStage<T> m() {
      return CompletableFuture.completedFuture(null);
    }
  }

  public static class NestedCompletionStageBean {
    @McpTool
    public CompletionStage<CompletionStage<Person>> m() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Person("Ada", 36)));
    }
  }

  public static class FutureOfStageBean {
    @McpTool
    public CompletableFuture<CompletionStage<Person>> m() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Person("Ada", 36)));
    }
  }
}
