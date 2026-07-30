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
package com.callibrity.mocapi.server.elicitation;

import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.server.util.ScopedValueResolver;

/**
 * Resolves {@link McpElicitor} parameters in tool, prompt, and resource handler methods by reading
 * from the {@link McpElicitor#CURRENT} {@link ScopedValue} (ADR-0024).
 */
public class McpElicitorResolver extends ScopedValueResolver<McpElicitor> {

  public McpElicitorResolver() {
    super(McpElicitor.class, McpElicitor.CURRENT);
  }
}
