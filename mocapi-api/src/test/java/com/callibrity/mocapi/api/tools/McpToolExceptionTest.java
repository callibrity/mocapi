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
package com.callibrity.mocapi.api.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolExceptionTest {

  @Nested
  class Message_only_constructor {
    @Test
    void preserves_message_and_has_null_structured_content_and_empty_additional_content() {
      var e = new McpToolException("oops");
      assertThat(e).hasMessage("oops");
      assertThat(e.getStructuredContent()).isNull();
      assertThat(e.getAdditionalContent()).isEmpty();
      assertThat(e.getCause()).isNull();
    }
  }

  @Nested
  class Message_plus_cause_constructor {
    @Test
    void preserves_message_and_cause() {
      var cause = new IllegalStateException("root");
      var e = new McpToolException("wrapper", cause);
      assertThat(e).hasMessage("wrapper").hasCause(cause);
      assertThat(e.getStructuredContent()).isNull();
    }
  }

  @Nested
  class Message_plus_structured_content_constructor {
    @Test
    void preserves_structured_content_payload_verbatim() {
      var payload = new ErrorDetails("NOT_FOUND", "no such user");
      var e = new McpToolException("missing", payload);
      assertThat(e.getStructuredContent()).isSameAs(payload);
      assertThat(e.getCause()).isNull();
    }

    @Test
    void accepts_null_structured_content() {
      var e = new McpToolException("oops", (Object) null);
      assertThat(e.getStructuredContent()).isNull();
    }
  }

  @Nested
  class Full_constructor {
    @Test
    void preserves_all_three_parameters() {
      var cause = new RuntimeException("boom");
      var payload = new ErrorDetails("TIMEOUT", "took too long");
      var e = new McpToolException("wrapper", payload, cause);
      assertThat(e).hasMessage("wrapper").hasCause(cause);
      assertThat(e.getStructuredContent()).isSameAs(payload);
    }
  }

  @Nested
  class Subclass_customization {

    @Test
    void subclass_can_override_structured_content_without_threading_value_through_super() {
      var e = new UserNotFoundException("alice");
      assertThat(e).hasMessage("User not found: alice");
      assertThat(e.getStructuredContent())
          .isInstanceOfSatisfying(
              UserNotFoundException.Details.class,
              d -> {
                assertThat(d.code()).isEqualTo("USER_NOT_FOUND");
                assertThat(d.username()).isEqualTo("alice");
              });
    }

    @Test
    void subclass_can_override_additional_content_blocks() {
      var e = new RichErrorException();
      assertThat(e.getAdditionalContent())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class, tc -> assertThat(tc.text()).isEqualTo("see troubleshooting.md"));
    }

    @Test
    void mocapi_catches_parent_type_so_subclass_instance_is_assignable_to_it() {
      McpToolException e = new UserNotFoundException("alice");
      assertThat(e).isInstanceOf(McpToolException.class);
    }
  }

  // --- test fixtures ---

  record ErrorDetails(String code, String description) {}

  static class UserNotFoundException extends McpToolException {
    private final String username;

    UserNotFoundException(String username) {
      super("User not found: " + username);
      this.username = username;
    }

    @Override
    public Object getStructuredContent() {
      return new Details("USER_NOT_FOUND", username);
    }

    record Details(String code, String username) {}
  }

  static class RichErrorException extends McpToolException {
    RichErrorException() {
      super("something went wrong");
    }

    @Override
    public List<ContentBlock> getAdditionalContent() {
      return List.of(new TextContent("see troubleshooting.md", null));
    }
  }
}
