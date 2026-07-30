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
package com.callibrity.mocapi.server.context;

import com.callibrity.mocapi.api.context.MrtrContext;
import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.api.progress.CountingProgressEmitter;
import com.callibrity.mocapi.api.progress.DoubleProgressEmitter;
import com.callibrity.mocapi.api.progress.LongProgressEmitter;
import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.api.progress.PercentageCompleteProgressEmitter;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.elicitation.ElicitationDispatcher;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.progress.DefaultMcpProgressSource;
import tools.jackson.databind.node.ValueNode;

/**
 * Shared {@link MrtrContext} implementation for the three MRTR-capable handler kinds (ADR-0025).
 * Progress delegates to a {@link DefaultMcpProgressSource}; elicitation routes through the {@link
 * ElicitationDispatcher} seam (the MRTR replay engine in production, ADR-0021) after a per-request
 * capability check against the request's {@link McpExchange} — a client that did not declare the
 * {@code elicitation} capability gets {@link McpElicitationNotSupportedException}, surfaced on the
 * wire as the spec's {@code MissingRequiredClientCapabilityError}.
 *
 * <p>Concrete subclasses ({@code DefaultMcpToolContext}, {@code DefaultMcpPromptContext}, {@code
 * DefaultMcpResourceContext}) add nothing but their kind's marker interface, which the parameter
 * resolvers key on.
 */
public abstract class AbstractMrtrContext implements MrtrContext {

  private final McpProgressSource progress;
  private final ElicitationDispatcher elicitationDispatcher;
  private final McpExchange exchange;
  private final String handlerName;

  protected AbstractMrtrContext(
      McpTransport transport,
      ValueNode progressToken,
      ElicitationDispatcher elicitationDispatcher,
      McpExchange exchange,
      String handlerName) {
    this.progress = new DefaultMcpProgressSource(transport, progressToken);
    this.elicitationDispatcher = elicitationDispatcher;
    this.exchange = exchange;
    this.handlerName = handlerName;
  }

  @Override
  public String handlerName() {
    return handlerName;
  }

  @Override
  public DoubleProgressEmitter doubleProgress(Double total) {
    return progress.doubleProgress(total);
  }

  @Override
  public LongProgressEmitter longProgress(Long total) {
    return progress.longProgress(total);
  }

  @Override
  public CountingProgressEmitter countingProgress(Long total) {
    return progress.countingProgress(total);
  }

  @Override
  public PercentageCompleteProgressEmitter percentProgress() {
    return progress.percentProgress();
  }

  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    if (exchange == null || !exchange.supportsElicitationForm()) {
      throw new McpElicitationNotSupportedException(
          "Client did not declare the elicitation capability in clientCapabilities");
    }
    return elicitationDispatcher.elicit(params);
  }
}
