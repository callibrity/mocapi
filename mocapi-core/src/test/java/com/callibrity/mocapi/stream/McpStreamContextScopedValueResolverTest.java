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
package com.callibrity.mocapi.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.param.ParameterInfo;

class McpStreamContextScopedValueResolverTest {

  private final McpStreamContextScopedValueResolver resolver =
      new McpStreamContextScopedValueResolver();

  @Test
  void supportsShouldReturnTrueForMcpStreamContextType() {
    ParameterInfo info = mock(ParameterInfo.class);
    doReturn(McpStreamContext.class).when(info).resolvedType();
    assertThat(resolver.supports(info)).isTrue();
  }

  @Test
  void supportsShouldReturnFalseForOtherType() {
    ParameterInfo info = mock(ParameterInfo.class);
    doReturn(String.class).when(info).resolvedType();
    assertThat(resolver.supports(info)).isFalse();
  }

  @Test
  void resolveShouldReturnNullWhenNotBound() {
    ParameterInfo info = mock(ParameterInfo.class);
    assertThat(resolver.resolve(info, null)).isNull();
  }

  @Test
  void resolveShouldReturnContextWhenBound() throws Exception {
    ParameterInfo info = mock(ParameterInfo.class);
    McpStreamContext<?> ctx = mock(DefaultMcpStreamContext.class);
    Object result =
        ScopedValue.where(McpStreamContext.CURRENT, ctx).call(() -> resolver.resolve(info, null));
    assertThat(result).isSameAs(ctx);
  }
}
