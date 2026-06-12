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
package com.callibrity.mocapi.server.elicitation;

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.server.exchange.McpExchange;
import java.util.Objects;

/**
 * The {@link McpElicitor} bound during prompt and resource dispatch (ADR-0024). Tool dispatch binds
 * its {@code McpToolContext} instead, which carries the same semantics: a capability pre-check
 * against the request's exchange, then the {@link ElicitationDispatcher} seam (the MRTR replay
 * engine in production, ADR-0021).
 */
public class DefaultMcpElicitor implements McpElicitor {

  private final ElicitationDispatcher dispatcher;

  public DefaultMcpElicitor(ElicitationDispatcher dispatcher) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
  }

  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    McpExchange exchange = McpExchange.CURRENT.isBound() ? McpExchange.CURRENT.get() : null;
    if (exchange == null || !exchange.supportsElicitationForm()) {
      throw new McpElicitationNotSupportedException(
          "Client did not declare the elicitation capability in clientCapabilities");
    }
    return dispatcher.elicit(params);
  }
}
