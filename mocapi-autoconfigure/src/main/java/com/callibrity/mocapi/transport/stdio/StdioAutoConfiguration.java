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
package com.callibrity.mocapi.transport.stdio;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the stateless stdio transport (MCP 2026-07-28). Enabled by {@code
 * mocapi.stdio.enabled=true}; clients that launch the server as a subprocess (Claude Desktop and
 * friends) should set this in {@code application.properties} or as a command-line override.
 */
@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@ConditionalOnClass(StdioTransport.class)
@ConditionalOnBean(McpServer.class)
@ConditionalOnProperty(prefix = "mocapi.stdio", name = "enabled", havingValue = "true")
public class StdioAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public StdioTransport mcpStdioTransport(ObjectMapper objectMapper) {
    // NOSONAR java:S106 — stdout IS the protocol output channel for this transport.
    return new StdioTransport(objectMapper, System.out); // NOSONAR
  }

  @Bean
  @ConditionalOnMissingBean
  public StdioServer mcpStdioServer(
      McpServer server, ObjectMapper objectMapper, StdioTransport mcpStdioTransport) {
    return new StdioServer(server, objectMapper, mcpStdioTransport, StdioServer.stdin());
  }

  @Bean
  public ApplicationRunner mcpStdioRunner(StdioServer mcpStdioServer) {
    return args -> mcpStdioServer.run();
  }
}
