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
package com.callibrity.mocapi.o11y;

import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import com.callibrity.ripcurl.o11y.JsonRpcObservationCustomizer;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.util.ClassUtils;

/**
 * Composes the MCP observability surface per the OpenTelemetry MCP semantic conventions (ADR-0030):
 *
 * <ul>
 *   <li>{@link McpServerOperationCustomizer} — implements ripcurl's {@link
 *       JsonRpcObservationCustomizer} seam, attaching the semconv {@code mcp.server.operation}
 *       observation (span {@code {method} {target}}, metric {@code mcp.server.operation.duration})
 *       to every {@code @JsonRpcMethod} handler. This is the span that joins the client's trace
 *       when the request {@code _meta} carries W3C trace context. Because the bean occupies
 *       ripcurl's observation-owner seam, ripcurl's default {@code jsonrpc.server} observation
 *       backs off automatically — one observation per dispatch, owned by the most specific
 *       convention, with the JSON-RPC attributes carried on the MCP span as the conventions
 *       prescribe. This autoconfiguration is ordered before ripcurl's so the back-off condition
 *       sees the bean.
 *   <li>Four per-handler {@link McpHandlerObservationInterceptor} customizer beans (tool / prompt /
 *       resource / resource-template) — the mocapi-specific inner {@code mcp.handler.execution}
 *       observation isolating user-handler time from framework overhead. Only fires for methods
 *       that route through a mocapi handler; dispatch-only methods ({@code tools/list}, {@code
 *       server/discover}, notifications) emit only the server-operation observation.
 * </ul>
 *
 * <p>{@code network.transport} is resolved once at configuration time from the transport module on
 * the classpath — {@code tcp} for Streamable HTTP, {@code pipe} for stdio (the semconv value for
 * pipe-based transports). When both transports are present, HTTP wins; deployments run one
 * transport in practice.
 *
 * <p>MCP 2026-07-28 is sessionless, so there is no {@code mcp.session.id} attribute and no
 * session-duration metrics (ADR-0020, ADR-0030).
 *
 * <p>Activates only when an {@link ObservationRegistry} bean is present — Spring Boot auto-creates
 * one whenever Actuator or any Micrometer Observation autoconfiguration is on the classpath, so
 * this autoconfig lights up automatically when paired with a metrics or tracing stack (Spring
 * Boot's {@code spring-boot-starter-opentelemetry}, an Azure Monitor bridge, a Datadog registry,
 * etc.).
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
    // Ordered before ripcurl's observation autoconfig so its @ConditionalOnMissingBean back-off
    // sees the McpServerOperationCustomizer bean. Without this the default could double-register.
    beforeName = "com.callibrity.ripcurl.autoconfigure.RipCurlObservationAutoConfiguration")
@ConditionalOnClass({McpServerOperationInterceptor.class, ObservationRegistry.class})
@ConditionalOnBean(ObservationRegistry.class)
public class MocapiO11yAutoConfiguration {

  private static final String HTTP_TRANSPORT_CLASS =
      "com.callibrity.mocapi.transport.http.StreamableHttpTransport";
  private static final String STDIO_TRANSPORT_CLASS =
      "com.callibrity.mocapi.transport.stdio.StdioServer";

  /**
   * Attaches the semconv {@code mcp.server.operation} observation to every JSON-RPC method handler
   * and, by occupying ripcurl's {@link JsonRpcObservationCustomizer} seam, replaces ripcurl's
   * default {@code jsonrpc.server} observation. The translator registry is the same one the
   * dispatcher uses, so error codes on the observation match the wire.
   */
  @Bean
  @ConditionalOnBean(JsonRpcExceptionTranslatorRegistry.class)
  public McpServerOperationCustomizer mcpServerOperationCustomizer(
      ObservationRegistry registry, JsonRpcExceptionTranslatorRegistry translators) {
    return new McpServerOperationCustomizer(registry, translators, networkTransport());
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

  private static String networkTransport() {
    ClassLoader classLoader = MocapiO11yAutoConfiguration.class.getClassLoader();
    if (ClassUtils.isPresent(HTTP_TRANSPORT_CLASS, classLoader)) {
      return McpServerOperationInterceptor.TRANSPORT_TCP;
    }
    if (ClassUtils.isPresent(STDIO_TRANSPORT_CLASS, classLoader)) {
      return McpServerOperationInterceptor.TRANSPORT_PIPE;
    }
    return null;
  }
}
