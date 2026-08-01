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
package com.callibrity.mocapi.examples.apps;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables CORS on the MCP endpoint so a <em>browser-based</em> MCP Apps host (e.g. the ext-apps
 * {@code basic-host} reference implementation, or a web client) can reach this server cross-origin.
 * Without this, the browser blocks the {@code fetch} (and its preflight) because mocapi emits no
 * {@code Access-Control-*} headers of its own.
 *
 * <p>This is separate from — and does not weaken — mocapi's Origin allowlist (DNS-rebinding
 * protection): that still validates the {@code Origin} header host-side. The default allowlist
 * already permits {@code localhost}, so a host on {@code http://localhost:8080} passes both checks.
 *
 * <p>Scoped to local development origins by default; override with {@code
 * mocapi.example.cors-origins} (comma-separated) to point a different browser host at this server.
 * This is an example convenience, not a production CORS policy.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final String[] allowedOrigins;
  private final String endpoint;

  public CorsConfig(
      @Value("${mocapi.example.cors-origins:http://localhost:8080,http://127.0.0.1:8080}")
          String[] allowedOrigins,
      @Value("${mocapi.endpoint:/mcp}") String endpoint) {
    this.allowedOrigins = allowedOrigins;
    this.endpoint = endpoint;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping(endpoint)
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("*");
  }
}
