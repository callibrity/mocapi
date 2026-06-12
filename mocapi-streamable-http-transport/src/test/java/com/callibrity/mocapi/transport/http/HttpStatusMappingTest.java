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

import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HttpStatusMappingTest {

  @ParameterizedTest(name = "{0} maps to {1}")
  @CsvSource({
    "-32700, BAD_REQUEST", // parse error
    "-32600, BAD_REQUEST", // invalid request
    "-32601, NOT_FOUND", // method not found — modern server, not a legacy 404
    "-32602, BAD_REQUEST", // invalid params (incl. missing _meta envelope)
    "-32001, BAD_REQUEST", // HeaderMismatch (transport prose)
    "-32003, BAD_REQUEST", // MissingRequiredClientCapabilityError
    "-32004, BAD_REQUEST", // UnsupportedProtocolVersionError
    "-32603, OK", // internal error — valid JSON-RPC response, HTTP exchange succeeded
    "-32010, OK", // mocapi guard denial — application-level error
    "1234, OK" // application-defined error
  })
  void maps_error_code_to_http_status(int code, HttpStatus expected) {
    assertThat(HttpStatusMapping.forErrorCode(code)).isEqualTo(expected);
  }

  @Test
  void result_maps_to_ok() {
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    assertThat(HttpStatusMapping.forResponse(result)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void error_response_maps_through_its_code() {
    var error = new JsonRpcError(-32601, "not found", JsonNodeFactory.instance.numberNode(1));

    assertThat(HttpStatusMapping.forResponse(error)).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
