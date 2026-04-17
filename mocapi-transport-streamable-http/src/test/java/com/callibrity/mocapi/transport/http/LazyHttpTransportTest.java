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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

class LazyHttpTransportTest {

  @Test
  void sendingResponseFromPendingCompletesFutureAsJson() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport = new LazyHttpTransport(future, unusedStreamFactory(), unusedEmitterFactory());

    var result = new JsonRpcResult(JsonNodeFactory.instance.objectNode().put("k", "v"), intNode(1));
    transport.send(result);

    ResponseEntity<Object> entity = future.getNow(null);
    assertThat(entity).isNotNull();
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(entity.getBody()).isSameAs(result);
  }

  private static Supplier<OdysseyStream<JsonRpcMessage>> unusedStreamFactory() {
    return () -> {
      throw new AssertionError("should not be called");
    };
  }

  private static Function<OdysseyStream<JsonRpcMessage>, SseEmitter> unusedEmitterFactory() {
    return stream -> {
      throw new AssertionError("should not be called");
    };
  }

  private static tools.jackson.databind.JsonNode intNode(int value) {
    return JsonNodeFactory.instance.numberNode(value);
  }
}
