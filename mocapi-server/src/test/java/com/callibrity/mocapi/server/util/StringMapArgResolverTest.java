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
package com.callibrity.mocapi.server.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.specular.TypeRef;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StringMapArgResolverTest {

  enum Color {
    RED,
    BLUE
  }

  void stringArg(String name) {
    // reflective target
  }

  void intArg(int age) {
    // reflective target
  }

  void enumArg(Color color) {
    // reflective target
  }

  void mapArg(Map<String, String> all) {
    // reflective target
  }

  interface Unconvertible {}

  void unsupportedArg(Unconvertible blob) {
    // reflective target
  }

  private final ConversionService conversionService = DefaultConversionService.getSharedInstance();
  private final StringMapArgResolver resolver = new StringMapArgResolver(conversionService);

  @Test
  void binds_string_parameter() {
    assertThat(resolver.bind(paramInfo("stringArg", String.class))).isPresent();
  }

  @Test
  void binds_convertible_parameter() {
    assertThat(resolver.bind(paramInfo("intArg", int.class))).isPresent();
    assertThat(resolver.bind(paramInfo("enumArg", Color.class))).isPresent();
  }

  @Test
  void binds_map_parameter() {
    assertThat(resolver.bind(paramInfo("mapArg", Map.class))).isPresent();
  }

  @Test
  void does_not_bind_unconvertible_parameter() {
    assertThat(resolver.bind(paramInfo("unsupportedArg", Unconvertible.class))).isEmpty();
  }

  @Test
  void resolves_whole_map_for_map_parameter() {
    var binding = resolver.bind(paramInfo("mapArg", Map.class)).orElseThrow();
    var args = Map.of("a", "1", "b", "2");

    assertThat(binding.resolve(args)).isEqualTo(args);
  }

  @Test
  void resolves_empty_map_for_map_parameter_when_arguments_are_null() {
    var binding = resolver.bind(paramInfo("mapArg", Map.class)).orElseThrow();

    assertThat(binding.resolve(null)).isEqualTo(Map.of());
  }

  @Test
  void resolves_string_value_by_parameter_name() {
    var binding = resolver.bind(paramInfo("stringArg", String.class)).orElseThrow();

    assertThat(binding.resolve(Map.of("name", "alice"))).isEqualTo("alice");
  }

  @Test
  void converts_value_to_declared_type() {
    var binding = resolver.bind(paramInfo("intArg", int.class)).orElseThrow();

    assertThat(binding.resolve(Map.of("age", "42"))).isEqualTo(42);
  }

  @Test
  void converts_value_to_enum() {
    var binding = resolver.bind(paramInfo("enumArg", Color.class)).orElseThrow();

    assertThat(binding.resolve(Map.of("color", "RED"))).isEqualTo(Color.RED);
  }

  @Test
  void returns_null_when_argument_missing() {
    var binding = resolver.bind(paramInfo("stringArg", String.class)).orElseThrow();

    assertThat(binding.resolve(Map.of())).isNull();
  }

  @Test
  void returns_null_when_arguments_are_null_for_scalar_parameter() {
    var binding = resolver.bind(paramInfo("stringArg", String.class)).orElseThrow();

    assertThat(binding.resolve(null)).isNull();
  }

  @Test
  void wraps_conversion_failure_in_parameter_resolution_exception() {
    var binding = resolver.bind(paramInfo("intArg", int.class)).orElseThrow();

    var args = Map.of("age", "not-a-number");
    assertThatThrownBy(() -> binding.resolve(args))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("age")
        .hasMessageContaining("int");
  }

  private static ParameterInfo paramInfo(String methodName, Class<?> paramType) {
    Method method;
    try {
      method = StringMapArgResolverTest.class.getDeclaredMethod(methodName, paramType);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
    Parameter parameter = method.getParameters()[0];
    return ParameterInfo.of(parameter, 0, TypeRef.parameterType(parameter));
  }
}
