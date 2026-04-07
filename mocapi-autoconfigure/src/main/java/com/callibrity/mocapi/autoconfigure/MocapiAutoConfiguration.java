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
package com.callibrity.mocapi.autoconfigure;

import com.callibrity.mocapi.autoconfigure.sse.McpSessionManager;
import com.callibrity.mocapi.autoconfigure.sse.McpStreamingController;
import com.callibrity.mocapi.server.JsonRpcMessages;
import com.callibrity.mocapi.server.McpMethodHandler;
import com.callibrity.mocapi.server.McpMethodRegistry;
import com.callibrity.mocapi.server.McpProtocol;
import com.callibrity.mocapi.server.McpRequestValidator;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpServerCapability;
import com.callibrity.mocapi.server.exception.McpInternalErrorException;
import com.callibrity.mocapi.tools.McpToolsCapability;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@AutoConfiguration
@EnableConfigurationProperties(MocapiProperties.class)
@PropertySource("classpath:mocapi-defaults.properties")
@RequiredArgsConstructor
public class MocapiAutoConfiguration {

  // ------------------------------ FIELDS ------------------------------

  private final MocapiProperties props;

  // -------------------------- OTHER METHODS --------------------------

  @Bean
  public McpServer mcpServer(List<McpServerCapability> serverCapabilities) {
    return new McpServer(serverCapabilities, props.getServerInfo(), props.getInstructions());
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public McpSessionManager mcpSessionManager(OdysseyStreamRegistry registry) {
    return new McpSessionManager(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpProtocol mcpProtocol(
      McpServer mcpServer,
      ObjectMapper objectMapper,
      @Autowired(required = false) McpToolsCapability toolsCapability) {
    McpRequestValidator validator = new McpRequestValidator(props.getAllowedOrigins());
    JsonRpcMessages messages = new JsonRpcMessages(objectMapper);
    McpMethodRegistry.Builder registryBuilder =
        McpMethodRegistry.builder()
            .register(
                "initialize",
                new McpMethodHandler.Json(
                    params -> initializeServer(mcpServer, objectMapper, params)))
            .register("ping", new McpMethodHandler.Json(_ -> mcpServer.ping()))
            .register(
                "notifications/initialized",
                new McpMethodHandler.Json(
                    _ -> {
                      mcpServer.clientInitialized();
                      return null;
                    }));

    if (toolsCapability != null) {
      registryBuilder
          .register(
              "tools/list",
              new McpMethodHandler.Json(
                  params ->
                      toolsCapability.listTools(
                          params != null ? params.path("cursor").asString(null) : null)))
          .register(
              "tools/call",
              new McpMethodHandler.Json(params -> callTool(toolsCapability, objectMapper, params)));
    }

    return new McpProtocol(validator, registryBuilder.build(), messages);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpStreamingController mcpStreamingController(
      McpProtocol mcpProtocol,
      McpSessionManager sessionManager,
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper) {
    return new McpStreamingController(mcpProtocol, sessionManager, registry, objectMapper);
  }

  private static McpServer.InitializeResponse initializeServer(
      McpServer mcpServer, ObjectMapper objectMapper, JsonNode params) {
    try {
      return mcpServer.initialize(
          params.path("protocolVersion").asString(),
          objectMapper.treeToValue(
              params.get("capabilities"), com.callibrity.mocapi.client.ClientCapabilities.class),
          objectMapper.treeToValue(
              params.get("clientInfo"), com.callibrity.mocapi.client.ClientInfo.class));
    } catch (tools.jackson.core.JacksonException e) {
      throw new McpInternalErrorException("Failed to deserialize initialize params", e);
    }
  }

  private static McpToolsCapability.CallToolResponse callTool(
      McpToolsCapability toolsCapability, ObjectMapper objectMapper, JsonNode params) {
    JsonNode argsNode = params.path("arguments");
    ObjectNode arguments =
        argsNode.isObject() ? (ObjectNode) argsNode : objectMapper.createObjectNode();
    return toolsCapability.callTool(params.path("name").asString(), arguments);
  }
}
