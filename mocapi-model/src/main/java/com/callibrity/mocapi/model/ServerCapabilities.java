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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

/**
 * Capabilities the server advertises in {@code DiscoverResult}. {@code extensions} keys are
 * reverse-DNS extension identifiers (SEP-2133). The {@code logging} member is deprecated
 * (SEP-2577).
 */
// SEP-2577 spec contract: the deprecated logging capability member remains in the specification
// for the deprecation window; modeling it is required for 1:1 fidelity.
// java:S1133 — this deprecation is mandated by the spec, not scheduled for our removal:
// MCP 2026-07-28 still defines the type and SEP-2577 holds it for a 12-month window, and
// mocapi-model mirrors schema.ts 1:1 (ADR-0014). Removing it would make mocapi a less
// faithful implementation. Revisit when the spec drops it, not before.
@SuppressWarnings({"deprecation", "java:S1133"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerCapabilities(
    Map<String, ObjectNode> experimental,
    ToolsCapability tools,
    @Deprecated(since = "2026-07-28") LoggingCapability logging,
    CompletionsCapability completions,
    ResourcesCapability resources,
    PromptsCapability prompts,
    Map<String, ObjectNode> extensions) {

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Mutable builder for {@link ServerCapabilities}, seeded with mocapi's defaults. Extension
   * modules contribute at startup via {@code ServerCapabilitiesCustomizer} — most commonly by
   * declaring their capability through {@link #extension(String, ObjectNode)} — before the {@code
   * server/discover} response is assembled (ADR-0031). Building with no customizations reproduces
   * the historical hardcoded defaults exactly.
   */
  public static final class Builder {
    private Map<String, ObjectNode> experimental;
    private ToolsCapability tools = new ToolsCapability(false);
    private LoggingCapability logging;
    private CompletionsCapability completions = new CompletionsCapability();
    private ResourcesCapability resources = new ResourcesCapability(false, false);
    private PromptsCapability prompts = new PromptsCapability(false);
    private final Map<String, ObjectNode> extensions = new LinkedHashMap<>();

    private Builder() {}

    public Builder experimental(Map<String, ObjectNode> experimental) {
      this.experimental = experimental;
      return this;
    }

    public Builder tools(ToolsCapability tools) {
      this.tools = tools;
      return this;
    }

    public Builder logging(LoggingCapability logging) {
      this.logging = logging;
      return this;
    }

    public Builder completions(CompletionsCapability completions) {
      this.completions = completions;
      return this;
    }

    public Builder resources(ResourcesCapability resources) {
      this.resources = resources;
      return this;
    }

    public Builder prompts(PromptsCapability prompts) {
      this.prompts = prompts;
      return this;
    }

    /**
     * Declares support for an MCP extension keyed by its reverse-DNS identifier (SEP-2133), e.g.
     * {@code "io.modelcontextprotocol/tasks"}. {@code config} is the extension's capability object,
     * often an empty object. A later call with the same id replaces the earlier value.
     */
    public Builder extension(String id, ObjectNode config) {
      extensions.put(id, config);
      return this;
    }

    public ServerCapabilities build() {
      return new ServerCapabilities(
          experimental,
          tools,
          logging,
          completions,
          resources,
          prompts,
          extensions.isEmpty() ? Map.of() : Map.copyOf(extensions));
    }
  }
}
