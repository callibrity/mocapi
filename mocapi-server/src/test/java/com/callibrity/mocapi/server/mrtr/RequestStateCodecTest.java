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
package com.callibrity.mocapi.server.mrtr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RequestStateCodecTest {

  private static final String SECRET =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
  private static final String OTHER_SECRET =
      Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

  private final ObjectMapper mapper = new ObjectMapper();
  private final RequestStateCodec codec =
      RequestStateCodec.withSecret(SECRET, Duration.ofMinutes(5), mapper);

  private ObjectNode originalParams() {
    ObjectNode params = mapper.createObjectNode();
    params.put("name", "onboard");
    params.putObject("arguments").put("plan", "pro");
    return params;
  }

  private List<ResponseLedgerEntry> ledger() {
    ObjectNode content = mapper.createObjectNode();
    content.put("email", "user@example.com");
    return List.of(
        new ResponseLedgerEntry(
            "elicit-1", "sha256:abc", new ElicitResult(ElicitAction.ACCEPT, content)),
        new ResponseLedgerEntry("elicit-2", "sha256:def", null));
  }

  @Nested
  class Round_trip {

    @Test
    void decode_returns_what_encode_was_given() {
      String token = codec.encode("tools/call", originalParams(), ledger());

      RequestStatePayload payload = codec.decode(token);

      assertThat(payload.method()).isEqualTo("tools/call");
      assertThat(payload.originalParams()).isEqualTo(originalParams());
      assertThat(payload.inputResponses()).hasSize(2);
      assertThat(payload.inputResponses().get(0).key()).isEqualTo("elicit-1");
      assertThat(payload.inputResponses().get(0).fingerprint()).isEqualTo("sha256:abc");
      assertThat(payload.inputResponses().get(0).response().action())
          .isEqualTo(ElicitAction.ACCEPT);
      assertThat(payload.inputResponses().get(0).response().getString("email"))
          .isEqualTo("user@example.com");
      assertThat(payload.inputResponses().get(1).key()).isEqualTo("elicit-2");
      assertThat(payload.inputResponses().get(1).isAnswered()).isFalse();
    }

    @Test
    void decode_returns_the_principal_the_token_was_bound_to() {
      String token = codec.encode("tools/call", originalParams(), ledger(), "user-123");

      assertThat(codec.decode(token).principal()).isEqualTo("user-123");
    }

    @Test
    void encoding_without_a_principal_leaves_it_null() {
      String token = codec.encode("tools/call", originalParams(), ledger());

      assertThat(codec.decode(token).principal()).isNull();
    }

    @Test
    void each_encode_produces_a_different_token_for_the_same_payload() {
      String first = codec.encode("tools/call", originalParams(), ledger());
      String second = codec.encode("tools/call", originalParams(), ledger());

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ephemeral_key_codec_round_trips() {
      var ephemeral = RequestStateCodec.withEphemeralKey(Duration.ofMinutes(5), mapper);

      String token = ephemeral.encode("prompts/get", originalParams(), List.of());

      assertThat(ephemeral.decode(token).method()).isEqualTo("prompts/get");
    }
  }

  @Nested
  class Opacity {

    @Test
    void token_does_not_expose_the_payload_in_plaintext() {
      String token = codec.encode("tools/call", originalParams(), ledger());

      assertThat(token).doesNotContain("tools/call").doesNotContain("onboard");
      String decoded =
          new String(Base64.getUrlDecoder().decode(token), StandardCharsets.ISO_8859_1);
      assertThat(decoded)
          .doesNotContain("tools/call")
          .doesNotContain("onboard")
          .doesNotContain("user@example.com");
    }
  }

  @Nested
  class Tampering {

    @Test
    void flipping_a_ciphertext_byte_is_rejected() {
      String token = codec.encode("tools/call", originalParams(), ledger());
      byte[] bytes = Base64.getUrlDecoder().decode(token);
      bytes[bytes.length - 1] ^= 0x01;
      String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

      assertThatThrownBy(() -> codec.decode(tampered))
          .isInstanceOf(InvalidRequestStateException.class)
          .hasMessageContaining("authentication");
    }

    @Test
    void token_minted_under_a_different_key_is_rejected() {
      var other = RequestStateCodec.withSecret(OTHER_SECRET, Duration.ofMinutes(5), mapper);
      String token = other.encode("tools/call", originalParams(), ledger());

      assertThatThrownBy(() -> codec.decode(token))
          .isInstanceOf(InvalidRequestStateException.class)
          .hasMessageContaining("authentication");
    }

    @Test
    void garbage_that_is_not_base64url_is_rejected() {
      assertThatThrownBy(() -> codec.decode("not/base64+url!"))
          .isInstanceOf(InvalidRequestStateException.class)
          .hasMessageContaining("Base64URL");
    }

    @Test
    void token_shorter_than_a_nonce_is_rejected() {
      String tooShort = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[4]);

      assertThatThrownBy(() -> codec.decode(tooShort))
          .isInstanceOf(InvalidRequestStateException.class)
          .hasMessageContaining("too short");
    }
  }

  @Nested
  class Expiry {

    @Test
    void token_older_than_the_ttl_is_rejected_with_the_typed_exception() {
      Instant start = Instant.parse("2026-06-11T12:00:00Z");
      var mintingCodec =
          RequestStateCodec.withSecret(
              SECRET, Duration.ofMinutes(5), mapper, Clock.fixed(start, ZoneOffset.UTC));
      var lateCodec =
          RequestStateCodec.withSecret(
              SECRET,
              Duration.ofMinutes(5),
              mapper,
              Clock.fixed(start.plus(Duration.ofMinutes(5)).plusMillis(1), ZoneOffset.UTC));
      String token = mintingCodec.encode("tools/call", originalParams(), ledger());

      assertThatThrownBy(() -> lateCodec.decode(token))
          .isInstanceOf(ExpiredRequestStateException.class)
          .hasMessageContaining("expired");
    }

    @Test
    void token_exactly_at_the_ttl_boundary_is_still_accepted() {
      Instant start = Instant.parse("2026-06-11T12:00:00Z");
      var mintingCodec =
          RequestStateCodec.withSecret(
              SECRET, Duration.ofMinutes(5), mapper, Clock.fixed(start, ZoneOffset.UTC));
      var boundaryCodec =
          RequestStateCodec.withSecret(
              SECRET,
              Duration.ofMinutes(5),
              mapper,
              Clock.fixed(start.plus(Duration.ofMinutes(5)), ZoneOffset.UTC));
      String token = mintingCodec.encode("tools/call", originalParams(), ledger());

      assertThat(boundaryCodec.decode(token).method()).isEqualTo("tools/call");
    }
  }

  @Nested
  class Key_validation {

    @Test
    void secret_that_is_not_base64_is_rejected() {
      Duration ttl = Duration.ofMinutes(5);

      assertThatThrownBy(() -> RequestStateCodec.withSecret("///not-base64!", ttl, mapper))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Base64");
    }

    @Test
    void secret_of_the_wrong_length_is_rejected() {
      String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
      Duration ttl = Duration.ofMinutes(5);

      assertThatThrownBy(() -> RequestStateCodec.withSecret(shortSecret, ttl, mapper))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("256 bits");
    }
  }
}
