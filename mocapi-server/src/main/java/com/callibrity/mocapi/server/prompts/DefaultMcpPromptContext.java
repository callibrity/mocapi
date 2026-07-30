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
package com.callibrity.mocapi.server.prompts;

import com.callibrity.mocapi.api.prompts.McpPromptContext;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.context.AbstractMrtrContext;
import com.callibrity.mocapi.server.elicitation.ElicitationDispatcher;
import com.callibrity.mocapi.server.exchange.McpExchange;
import tools.jackson.databind.node.ValueNode;

/**
 * Default {@link McpPromptContext} implementation. Inherits progress emitters and elicitation from
 * {@link AbstractMrtrContext} (ADR-0025); adds nothing but the {@code McpPromptContext} marker the
 * prompt parameter resolver keys on.
 */
public class DefaultMcpPromptContext extends AbstractMrtrContext implements McpPromptContext {

  public DefaultMcpPromptContext(
      McpTransport transport,
      ValueNode progressToken,
      ElicitationDispatcher elicitationDispatcher,
      McpExchange exchange,
      String handlerName) {
    super(transport, progressToken, elicitationDispatcher, exchange, handlerName);
  }
}
