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
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.specular.TypeRef;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

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
  void supportsStringParameter() {
    assertThat(resolver.supports(paramInfo("stringArg", String.class))).isTrue();
  }

  @Test
  void supportsConvertibleParameter() {
    assertThat(resolver.supports(paramInfo("intArg", int.class))).isTrue();
    assertThat(resolver.supports(paramInfo("enumArg", Color.class))).isTrue();
  }

  @Test
  void supportsMapParameter() {
    assertThat(resolver.supports(paramInfo("mapArg", Map.class))).isTrue();
  }

  @Test
  void doesNotSupportUnconvertibleParameter() {
    assertThat(resolver.supports(paramInfo("unsupportedArg", Unconvertible.class))).isFalse();
  }

  @Test
  void resolvesWholeMapForMapParameter() {
    var info = paramInfo("mapArg", Map.class);
    var args = Map.of("a", "1", "b", "2");

    Object resolved = resolver.resolve(info, args);

    assertThat(resolved).isEqualTo(args);
  }

  @Test
  void resolvesEmptyMapForMapParameterWhenArgumentsAreNull() {
    var info = paramInfo("mapArg", Map.class);

    Object resolved = resolver.resolve(info, null);

    assertThat(resolved).isEqualTo(Map.of());
  }

  @Test
  void resolvesStringValueByParameterName() {
    var info = paramInfo("stringArg", String.class);

    Object resolved = resolver.resolve(info, Map.of("name", "alice"));

    assertThat(resolved).isEqualTo("alice");
  }

  @Test
  void convertsValueToDeclaredType() {
    var info = paramInfo("intArg", int.class);

    Object resolved = resolver.resolve(info, Map.of("age", "42"));

    assertThat(resolved).isEqualTo(42);
  }

  @Test
  void convertsValueToEnum() {
    var info = paramInfo("enumArg", Color.class);

    Object resolved = resolver.resolve(info, Map.of("color", "RED"));

    assertThat(resolved).isEqualTo(Color.RED);
  }

  @Test
  void returnsNullWhenArgumentMissing() {
    var info = paramInfo("stringArg", String.class);

    assertThat(resolver.resolve(info, Map.of())).isNull();
  }

  @Test
  void returnsNullWhenArgumentsAreNullForScalarParameter() {
    var info = paramInfo("stringArg", String.class);

    assertThat(resolver.resolve(info, null)).isNull();
  }

  @Test
  void wrapsConversionFailureInParameterResolutionException() {
    var info = paramInfo("intArg", int.class);

    var args = Map.of("age", "not-a-number");
    assertThatThrownBy(() -> resolver.resolve(info, args))
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
