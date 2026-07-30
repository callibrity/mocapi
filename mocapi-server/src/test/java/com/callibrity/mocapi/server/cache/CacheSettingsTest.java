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
package com.callibrity.mocapi.server.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CacheScope;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CacheSettingsTest {

  @Nested
  class Defaults {

    @Test
    void carry_zero_ttls_and_private_scope() {
      var settings = CacheSettings.defaults();

      assertThat(settings.listTtlMs()).isZero();
      assertThat(settings.readTtlMs()).isZero();
      assertThat(settings.scope()).isEqualTo(CacheScope.PRIVATE);
    }

    @Test
    void replace_null_components() {
      var settings = new CacheSettings(null, null, null);

      assertThat(settings.listTtl()).isEqualTo(Duration.ZERO);
      assertThat(settings.readTtl()).isEqualTo(Duration.ZERO);
      assertThat(settings.scope()).isEqualTo(CacheScope.PRIVATE);
    }
  }

  @Nested
  class Configured_values {

    @Test
    void surface_as_whole_milliseconds() {
      var settings =
          new CacheSettings(Duration.ofMinutes(2), Duration.ofSeconds(30), CacheScope.PUBLIC);

      assertThat(settings.listTtlMs()).isEqualTo(120_000L);
      assertThat(settings.readTtlMs()).isEqualTo(30_000L);
      assertThat(settings.scope()).isEqualTo(CacheScope.PUBLIC);
    }

    @Test
    void reject_negative_list_ttl() {
      var negative = Duration.ofSeconds(-1);
      assertThatThrownBy(() -> new CacheSettings(negative, Duration.ZERO, CacheScope.PRIVATE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
    }

    @Test
    void reject_negative_read_ttl() {
      var negative = Duration.ofSeconds(-1);
      assertThatThrownBy(() -> new CacheSettings(Duration.ZERO, negative, CacheScope.PRIVATE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
    }
  }
}
