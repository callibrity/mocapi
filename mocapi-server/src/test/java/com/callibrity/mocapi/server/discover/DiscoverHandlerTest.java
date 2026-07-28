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
package com.callibrity.mocapi.server.discover;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.cache.CacheSettings;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DiscoverHandlerTest {

  private final ServerCapabilities capabilities =
      new ServerCapabilities(null, new ToolsCapability(null), null, null, null, null, Map.of());
  private final DiscoverHandler handler = new DiscoverHandler("be nice", capabilities);

  @Test
  void advertises_the_supported_protocol_versions() {
    // The draft sentinel rides along during the RC window only (drop at Task 9.3).
    assertThat(handler.discover().supportedVersions())
        .containsExactly(McpServer.PROTOCOL_VERSION, McpServer.DRAFT_PROTOCOL_VERSION);
  }

  @Test
  void returns_the_configured_instructions() {
    var result = handler.discover();

    assertThat(result.instructions()).isEqualTo("be nice");
  }

  @Test
  void returns_the_configured_capabilities_with_empty_extensions() {
    var result = handler.discover();

    assertThat(result.capabilities()).isSameAs(capabilities);
    assertThat(result.capabilities().extensions()).isEmpty();
  }

  @Test
  void carries_required_cacheable_result_fields_with_conservative_defaults() {
    var result = handler.discover();

    assertThat(result.ttlMs()).isZero();
    assertThat(result.cacheScope()).isEqualTo(CacheScope.PRIVATE);
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
  }

  @Test
  void carries_configured_list_ttl_and_scope_when_cache_settings_are_supplied() {
    var settings = new CacheSettings(Duration.ofMinutes(10), Duration.ZERO, CacheScope.PUBLIC);
    var configured = new DiscoverHandler("be nice", capabilities, settings);

    var result = configured.discover();

    assertThat(result.ttlMs()).isEqualTo(600_000L);
    assertThat(result.cacheScope()).isEqualTo(CacheScope.PUBLIC);
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
  }
}
