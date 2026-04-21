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
package com.callibrity.mocapi.oauth2.metadata;

import com.callibrity.mocapi.model.Implementation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Mocapi baseline {@link McpMetadataCustomizer} that populates {@code resource_name} from the MCP
 * {@link Implementation} server-info bean — prefers {@code title}, falls back to {@code name}.
 * Contributes nothing when both fields are blank.
 */
public final class ResourceNameMetadataCustomizer implements McpMetadataCustomizer {

  private final String resourceName;

  public ResourceNameMetadataCustomizer(Implementation mcpServerInfo) {
    if (StringUtils.isNotBlank(mcpServerInfo.title())) {
      this.resourceName = mcpServerInfo.title();
    } else if (StringUtils.isNotBlank(mcpServerInfo.name())) {
      this.resourceName = mcpServerInfo.name();
    } else {
      this.resourceName = null;
    }
  }

  @Override
  public void customize(OAuth2ProtectedResourceMetadata.Builder builder) {
    if (resourceName != null) {
      builder.resourceName(resourceName);
    }
  }
}
