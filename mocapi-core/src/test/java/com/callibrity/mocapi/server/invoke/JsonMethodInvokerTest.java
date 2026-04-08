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
package com.callibrity.mocapi.server.invoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class JsonMethodInvokerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void shouldInvokeMethodAndReturnJsonResult() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("greet", String.class);
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();
    args.put("name", "World");

    var result = invoker.invoke(args);
    assertThat(result.isObject()).isTrue();
    assertThat(result.get("message").asString()).isEqualTo("Hello, World!");
  }

  @Test
  void shouldHandleMultipleParameters() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("add", int.class, int.class);
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();
    args.put("a", 3);
    args.put("b", 5);

    var result = invoker.invoke(args);
    assertThat(result.isObject()).isTrue();
    assertThat(result.get("sum").asInt()).isEqualTo(8);
  }

  @Test
  void shouldPropagateRuntimeExceptionUnwrapped() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("throwRuntime");
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();

    assertThatThrownBy(() -> invoker.invoke(args))
        .isInstanceOf(JsonRpcException.class)
        .hasMessage("bad params");
  }

  @Test
  void shouldWrapCheckedExceptionInJsonRpcException() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("throwChecked");
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();

    assertThatThrownBy(() -> invoker.invoke(args))
        .isInstanceOf(JsonRpcException.class)
        .hasMessage("Checked exception during method invocation")
        .hasCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("io failure");
  }

  @Test
  void shouldRethrowErrorDirectly() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("throwError");
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();

    assertThatThrownBy(() -> invoker.invoke(args))
        .isInstanceOf(StackOverflowError.class)
        .hasMessage("stack overflow");
  }

  @Test
  void shouldWrapIllegalAccessExceptionInJsonRpcException() throws Exception {
    var target = new TestService();
    Method method = PrivateService.class.getDeclaredMethod("secret");
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();

    assertThatThrownBy(() -> invoker.invoke(args))
        .isInstanceOf(JsonRpcException.class)
        .hasMessage("Cannot access method");
  }

  @Test
  void shouldHandleNullArguments() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("greet", String.class);
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();
    args.putNull("name");

    var result = invoker.invoke(args);
    assertThat(result.isObject()).isTrue();
    assertThat(result.get("message").asString()).isEqualTo("Hello, null!");
  }

  @Test
  void shouldHandleMissingArguments() throws Exception {
    var target = new TestService();
    Method method = TestService.class.getMethod("greet", String.class);
    var invoker = new JsonMethodInvoker(mapper, target, method);

    ObjectNode args = mapper.createObjectNode();

    var result = invoker.invoke(args);
    assertThat(result.isObject()).isTrue();
    assertThat(result.get("message").asString()).isEqualTo("Hello, null!");
  }

  public static class TestService {
    public GreetResponse greet(String name) {
      return new GreetResponse("Hello, " + name + "!");
    }

    public AddResponse add(int a, int b) {
      return new AddResponse(a + b);
    }

    public Object throwRuntime() {
      throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "bad params");
    }

    public Object throwChecked() throws IOException {
      throw new IOException("io failure");
    }

    public Object throwError() {
      throw new StackOverflowError("stack overflow");
    }
  }

  private static class PrivateService {
    private Object secret() {
      return "secret";
    }
  }

  public record GreetResponse(String message) {}

  public record AddResponse(int sum) {}
}
