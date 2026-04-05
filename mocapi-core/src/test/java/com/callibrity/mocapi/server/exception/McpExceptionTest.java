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
package com.callibrity.mocapi.server.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpExceptionTest {

  @Test
  void shouldStoreCodeAndMessage() {
    var exception = new McpException(-32600, "Invalid request");
    assertThat(exception.getCode()).isEqualTo(-32600);
    assertThat(exception.getMessage()).isEqualTo("Invalid request");
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void shouldStoreCodeMessageAndCause() {
    var cause = new IllegalArgumentException("bad arg");
    var exception = new McpException(-32600, "Invalid request", cause);
    assertThat(exception.getCode()).isEqualTo(-32600);
    assertThat(exception.getMessage()).isEqualTo("Invalid request");
    assertThat(exception.getCause()).isSameAs(cause);
  }
}
