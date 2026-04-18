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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Implementation;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Direct unit coverage for {@link MocapiOAuth2AutoConfiguration#resourceNameFor}. The two edge
 * branches — absent {@code Implementation} bean, and both {@code title} and {@code name} blank —
 * are unreachable from {@code @SpringBootTest} contexts in this module because the {@code
 * Implementation} bean is always on the classpath via {@code mocapi-streamable-http-transport}. A
 * direct call with a stubbed {@link ObjectProvider} is the honest way to cover them.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2ResourceNameTest {

  @Test
  void returns_empty_when_no_implementation_bean_available() {
    Optional<String> name = MocapiOAuth2AutoConfiguration.resourceNameFor(provider(null));
    assertThat(name).isEmpty();
  }

  @Test
  void returns_title_when_present() {
    Optional<String> name =
        MocapiOAuth2AutoConfiguration.resourceNameFor(
            provider(new Implementation("srv", "Nice Title", "1.0")));
    assertThat(name).contains("Nice Title");
  }

  @Test
  void falls_back_to_name_when_title_is_null() {
    Optional<String> name =
        MocapiOAuth2AutoConfiguration.resourceNameFor(
            provider(new Implementation("named-only", null, "1.0")));
    assertThat(name).contains("named-only");
  }

  @Test
  void falls_back_to_name_when_title_is_blank() {
    Optional<String> name =
        MocapiOAuth2AutoConfiguration.resourceNameFor(
            provider(new Implementation("named-only", "   ", "1.0")));
    assertThat(name).contains("named-only");
  }

  @Test
  void returns_empty_when_both_title_and_name_are_blank() {
    Optional<String> name =
        MocapiOAuth2AutoConfiguration.resourceNameFor(provider(new Implementation("", "", "1.0")));
    assertThat(name).isEmpty();
  }

  @Test
  void returns_empty_when_both_title_and_name_are_null() {
    Optional<String> name =
        MocapiOAuth2AutoConfiguration.resourceNameFor(
            provider(new Implementation(null, null, "1.0")));
    assertThat(name).isEmpty();
  }

  /**
   * Stub {@link ObjectProvider} whose {@code getIfAvailable} returns a fixed value. The full {@code
   * ObjectProvider} contract has many methods, but {@code resourceNameFor} only calls {@code
   * getIfAvailable()}, so only that one needs to work.
   */
  private static ObjectProvider<Implementation> provider(Implementation value) {
    return new ObjectProvider<>() {
      @Override
      public Implementation getIfAvailable() {
        return value;
      }

      @Override
      public Implementation getObject() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Implementation getObject(Object... args) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Implementation getIfUnique() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
