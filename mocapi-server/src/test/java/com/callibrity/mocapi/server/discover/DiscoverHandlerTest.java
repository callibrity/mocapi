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
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpServer;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DiscoverHandlerTest {

  private final Implementation serverInfo = new Implementation("test-server", "Test", "1.0", null);
  private final ServerCapabilities capabilities =
      new ServerCapabilities(null, new ToolsCapability(null), null, null, null, null, Map.of());
  private final DiscoverHandler handler = new DiscoverHandler(serverInfo, "be nice", capabilities);

  @Test
  void advertises_exactly_the_single_supported_protocol_version() {
    assertThat(handler.discover().supportedVersions()).containsExactly(McpServer.PROTOCOL_VERSION);
  }

  @Test
  void returns_server_identity_and_instructions() {
    var result = handler.discover();

    assertThat(result.serverInfo()).isSameAs(serverInfo);
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
}
