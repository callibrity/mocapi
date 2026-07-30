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
package com.callibrity.mocapi.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.MissingRequiredClientCapabilityErrorData;
import com.callibrity.mocapi.model.UnsupportedProtocolVersionErrorData;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Pins mocapi's JSON-RPC error codes to their literal wire values.
 *
 * <p>java:S3415 is suppressed because the assertions are not swapped. The named constant is the
 * actual value under test and the literal is the expected wire code, which is the correct AssertJ
 * order. The rule heuristically treats any named constant as the expected side. Literals are
 * deliberately spelled out so that changing a constant fails this test rather than silently
 * altering the wire protocol.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SuppressWarnings("java:S3415")
class JsonRpcErrorCodesTest {

  @Test
  void forbidden_is_pinned_to_the_mocapi_private_code() {
    assertThat(JsonRpcErrorCodes.FORBIDDEN).isEqualTo(-32010);
  }

  @Test
  void forbidden_does_not_collide_with_the_spec_defined_codes() {
    // MCP 2026-07-28 assigns -32021 (MissingRequiredClientCapability) and -32022
    // (UnsupportedProtocolVersion); -32020 is the transport's HeaderMismatch. ADR-0023.
    assertThat(JsonRpcErrorCodes.FORBIDDEN)
        .isNotEqualTo(MissingRequiredClientCapabilityErrorData.CODE)
        .isNotEqualTo(UnsupportedProtocolVersionErrorData.CODE)
        .isNotEqualTo(-32020);
  }

  @Test
  void spec_error_code_constants_match_the_schema() {
    assertThat(MissingRequiredClientCapabilityErrorData.CODE).isEqualTo(-32021);
    assertThat(UnsupportedProtocolVersionErrorData.CODE).isEqualTo(-32022);
  }
}
