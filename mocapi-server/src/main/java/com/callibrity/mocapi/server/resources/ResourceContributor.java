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
package com.callibrity.mocapi.server.resources;

import java.util.List;

/**
 * Supplies resource handlers to {@link McpResourcesService} at construction time (ADR-0035). Every
 * {@code ResourceContributor} bean is collected and its handlers merged into the one immutable
 * service — there is no runtime registration. The annotation scan of {@code @McpResource} /
 * {@code @McpResourceTemplate} methods is itself the primary, built-in contributor, not a
 * privileged path; an extension (e.g. MCP Apps) contributes from its own layer as a peer.
 *
 * <p>Both methods default to empty so an implementation overrides only the kind it produces.
 */
public interface ResourceContributor {

  /** Fixed-URI resource handlers this contributor provides. */
  default List<ReadResourceHandler> resources() {
    return List.of();
  }

  /** Templated resource handlers this contributor provides. */
  default List<ReadResourceTemplateHandler> resourceTemplates() {
    return List.of();
  }

  /**
   * Adapts a pair of already-built handler lists into a single {@link ResourceContributor}. Exists
   * so callers that assemble handlers directly (chiefly tests, which predate ADR-0035's
   * contributor-based construction) can still reach {@link McpResourcesService}'s single, primary
   * constructor without each call site hand-rolling an anonymous contributor.
   *
   * @param resources fixed-URI handlers to expose
   * @param resourceTemplates templated handlers to expose
   * @return a contributor returning exactly the given lists
   */
  static ResourceContributor of(
      List<ReadResourceHandler> resources, List<ReadResourceTemplateHandler> resourceTemplates) {
    return new ResourceContributor() {
      @Override
      public List<ReadResourceHandler> resources() {
        return resources;
      }

      @Override
      public List<ReadResourceTemplateHandler> resourceTemplates() {
        return resourceTemplates;
      }
    };
  }
}
