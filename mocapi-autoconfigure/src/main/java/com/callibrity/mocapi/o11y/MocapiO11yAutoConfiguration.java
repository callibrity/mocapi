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
package com.callibrity.mocapi.o11y;

import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Composes the MCP-layer observability surface on top of ripcurl's JSON-RPC observations:
 *
 * <ul>
 *   <li>{@link McpObservationFilter} — registered as a Micrometer {@code ObservationFilter} bean.
 *       Fires at {@code Observation.stop()} on ripcurl's {@code jsonrpc.server} observations and
 *       enriches them with {@code mcp.method.name}, {@code mcp.session.id}, and {@code
 *       mcp.protocol.version} tags (read from the bound {@link
 *       com.callibrity.ripcurl.core.JsonRpcDispatcher#CURRENT_REQUEST} and {@link
 *       com.callibrity.mocapi.server.session.McpSession#CURRENT} scoped values). Pure enrichment —
 *       no new observation is started at this layer.
 *   <li>Four per-handler {@link McpHandlerObservationInterceptor} customizer beans (tool / prompt /
 *       resource / resource-template) — attach a second, inner {@code mcp.handler.execution}
 *       observation so tool / prompt / resource execution shows up as a child span inside ripcurl's
 *       JSON-RPC span. Only fires for methods that route through a mocapi handler ({@code
 *       tools/call}, {@code prompts/get}, {@code resources/read}, {@code
 *       resources/templates/read}); dispatch-only methods like {@code tools/list}, {@code
 *       initialize}, and notifications emit only the outer JSON-RPC observation.
 * </ul>
 *
 * <p>Activates only when an {@link ObservationRegistry} bean is present — Spring Boot auto-creates
 * one whenever Actuator or any Micrometer Observation autoconfiguration is on the classpath, so
 * this autoconfig lights up automatically when paired with a metrics or tracing stack (Spring
 * Boot's {@code spring-boot-starter-opentelemetry}, an Azure Monitor bridge, a Datadog registry,
 * etc.).
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration")
@ConditionalOnClass({McpObservationFilter.class, ObservationRegistry.class})
@ConditionalOnBean(ObservationRegistry.class)
public class MocapiO11yAutoConfiguration {

  @Bean
  public McpObservationFilter mcpObservationFilter() {
    return new McpObservationFilter();
  }

  @Bean
  @Order(300)
  public CallToolHandlerCustomizer mcpToolHandlerObservationCustomizer(
      ObservationRegistry registry) {
    return config ->
        config.observationInterceptor(
            new McpHandlerObservationInterceptor(
                registry, HandlerKind.TOOL, config.descriptor().name()));
  }

  @Bean
  @Order(300)
  public GetPromptHandlerCustomizer mcpPromptHandlerObservationCustomizer(
      ObservationRegistry registry) {
    return config ->
        config.observationInterceptor(
            new McpHandlerObservationInterceptor(
                registry, HandlerKind.PROMPT, config.descriptor().name()));
  }

  @Bean
  @Order(300)
  public ReadResourceHandlerCustomizer mcpResourceHandlerObservationCustomizer(
      ObservationRegistry registry) {
    return config ->
        config.observationInterceptor(
            new McpHandlerObservationInterceptor(
                registry, HandlerKind.RESOURCE, config.descriptor().uri()));
  }

  @Bean
  @Order(300)
  public ReadResourceTemplateHandlerCustomizer mcpResourceTemplateHandlerObservationCustomizer(
      ObservationRegistry registry) {
    return config ->
        config.observationInterceptor(
            new McpHandlerObservationInterceptor(
                registry, HandlerKind.RESOURCE_TEMPLATE, config.descriptor().uriTemplate()));
  }
}
