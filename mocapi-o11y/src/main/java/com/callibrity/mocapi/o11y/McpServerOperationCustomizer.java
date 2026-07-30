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

import com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandlerConfig;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import com.callibrity.ripcurl.o11y.JsonRpcObservationCustomizer;
import io.micrometer.observation.ObservationRegistry;

/**
 * Mocapi's {@link JsonRpcObservationCustomizer}: attaches a {@link McpServerOperationInterceptor}
 * to every {@code @JsonRpcMethod} handler, so each dispatch is observed under the OpenTelemetry MCP
 * semantic conventions ({@code mcp.server.operation}) rather than the generic JSON-RPC ones
 * (ADR-0030). Because this bean implements ripcurl's {@code JsonRpcObservationCustomizer} seam,
 * ripcurl's default {@code jsonrpc.server} observation backs off automatically — one observation
 * per dispatch, owned by the most specific convention, with the JSON-RPC attributes carried on the
 * MCP span as the conventions prescribe.
 */
public final class McpServerOperationCustomizer implements JsonRpcObservationCustomizer {

  private final ObservationRegistry registry;
  private final JsonRpcExceptionTranslatorRegistry translators;
  private final String networkTransport;

  /**
   * @param registry the observation registry
   * @param translators the translator registry the dispatcher uses, so error codes on observations
   *     match the wire
   * @param networkTransport the semconv {@code network.transport} value ({@code tcp} / {@code
   *     pipe}), or {@code null} to omit the attribute
   */
  public McpServerOperationCustomizer(
      ObservationRegistry registry,
      JsonRpcExceptionTranslatorRegistry translators,
      String networkTransport) {
    this.registry = registry;
    this.translators = translators;
    this.networkTransport = networkTransport;
  }

  @Override
  public void customize(JsonRpcMethodHandlerConfig config) {
    config.interceptor(
        new McpServerOperationInterceptor(registry, translators, config.name(), networkTransport));
  }

  @Override
  public String toString() {
    return "Attaches the '"
        + McpServerOperationInterceptor.OBSERVATION_NAME
        + "' observation (OpenTelemetry MCP semantic conventions) to every @JsonRpcMethod handler";
  }
}
