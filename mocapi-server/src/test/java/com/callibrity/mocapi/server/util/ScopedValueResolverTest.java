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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.param.ParameterInfo;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ScopedValueResolverTest {

  private static final ScopedValue<String> TEST_VALUE = ScopedValue.newInstance();

  private final ScopedValueResolver<String> resolver =
      new ScopedValueResolver<>(String.class, TEST_VALUE) {};

  @Test
  void supports_matching_type() {
    var info = mock(ParameterInfo.class);
    doReturn(String.class).when(info).resolvedType();
    assertThat(resolver.supports(info)).isTrue();
  }

  @Test
  void does_not_support_non_matching_type() {
    var info = mock(ParameterInfo.class);
    doReturn(Integer.class).when(info).resolvedType();
    assertThat(resolver.supports(info)).isFalse();
  }

  @Test
  void resolves_value_when_bound() {
    var info = mock(ParameterInfo.class);
    var result = ScopedValue.where(TEST_VALUE, "hello").call(() -> resolver.resolve(info, null));
    assertThat(result).isEqualTo("hello");
  }

  @Test
  void returns_null_when_not_bound() {
    var info = mock(ParameterInfo.class);
    assertThat(resolver.resolve(info, null)).isNull();
  }
}
