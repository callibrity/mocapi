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

import com.callibrity.mocapi.model.DiscoverResult;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import java.util.List;

/**
 * Handles {@code server/discover} — the mandatory request that advertises the server's supported
 * protocol versions, capabilities, and identity (ADR-0019/0020). It replaces the removed {@code
 * initialize} handshake: discover is answerable at any time with no prior request, and a discover
 * carrying an unsupported protocol version gets {@code UnsupportedProtocolVersionError} whose
 * {@code data.supported} list serves as the version-bootstrap probe (the {@code _meta} envelope is
 * REQUIRED on discover like on every request — there is no envelope-less probe).
 *
 * <p>{@code DiscoverResult} is the sixth cacheable result. Like the list results, its payload is
 * startup-static discovery metadata, so it takes the configured <em>list</em> TTL ({@code
 * mocapi.cache.list-ttl}) and the configured scope ({@code mocapi.cache.scope}) from {@link
 * CacheSettings}; the defaults are the conservative {@code ttlMs=0} / {@code private}.
 */
public class DiscoverHandler {

  private final Implementation serverInfo;
  private final String instructions;
  private final ServerCapabilities capabilities;
  private final CacheSettings cacheSettings;

  public DiscoverHandler(
      Implementation serverInfo, String instructions, ServerCapabilities capabilities) {
    this(serverInfo, instructions, capabilities, CacheSettings.defaults());
  }

  public DiscoverHandler(
      Implementation serverInfo,
      String instructions,
      ServerCapabilities capabilities,
      CacheSettings cacheSettings) {
    this.serverInfo = serverInfo;
    this.instructions = instructions;
    this.capabilities = capabilities;
    this.cacheSettings = cacheSettings;
  }

  @JsonRpcMethod(McpMethods.SERVER_DISCOVER)
  public DiscoverResult discover() {
    return new DiscoverResult(
        List.of(McpServer.PROTOCOL_VERSION),
        capabilities,
        serverInfo,
        instructions,
        cacheSettings.listTtlMs(),
        cacheSettings.scope(),
        ResultTypes.COMPLETE);
  }
}
