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
package com.callibrity.mocapi.oauth2;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * Direct unit coverage for {@link MocapiOAuth2Compliance#validate}. Exercises the pure-logic method
 * without a Spring context.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2ComplianceValidationTest {

  @Test
  void passes_when_jwt_decoder_present_and_audiences_configured() {
    assertThatCode(
            () ->
                MocapiOAuth2Compliance.validate(mock(JwtDecoder.class), null, List.of("mcp-test")))
        .doesNotThrowAnyException();
  }

  @Test
  void passes_when_opaque_token_introspector_present_and_audiences_configured() {
    assertThatCode(
            () ->
                MocapiOAuth2Compliance.validate(
                    null, mock(OpaqueTokenIntrospector.class), List.of("mcp-test")))
        .doesNotThrowAnyException();
  }

  @Test
  void fails_when_both_jwt_decoder_and_opaque_introspector_are_missing() {
    assertThatThrownBy(() -> MocapiOAuth2Compliance.validate(null, null, List.of("mcp-test")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("neither JwtDecoder nor OpaqueTokenIntrospector");
  }

  @Test
  void fails_when_audiences_list_is_empty() {
    JwtDecoder jwt = mock(JwtDecoder.class);
    assertThatThrownBy(() -> MocapiOAuth2Compliance.validate(jwt, null, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audiences");
  }
}
