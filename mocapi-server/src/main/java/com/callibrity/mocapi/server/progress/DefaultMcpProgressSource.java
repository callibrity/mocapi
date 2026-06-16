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
package com.callibrity.mocapi.server.progress;

import com.callibrity.mocapi.api.progress.CountingProgressEmitter;
import com.callibrity.mocapi.api.progress.DoubleProgressEmitter;
import com.callibrity.mocapi.api.progress.LongProgressEmitter;
import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.api.progress.PercentageCompleteProgressEmitter;
import com.callibrity.mocapi.server.McpTransport;
import tools.jackson.databind.node.ValueNode;

/**
 * Default {@link McpProgressSource} for one handler invocation (ADR-0025). Captures the request's
 * transport and progress token once; each factory call mints a fresh, independently-stateful
 * emitter over a new {@link ProgressChannel}. A {@code null} progress token (client did not request
 * progress) yields emitters that validate but send nothing.
 */
public class DefaultMcpProgressSource implements McpProgressSource {

  private final McpTransport transport;
  private final ValueNode progressToken;

  public DefaultMcpProgressSource(McpTransport transport, ValueNode progressToken) {
    this.transport = transport;
    this.progressToken = progressToken;
  }

  @Override
  public DoubleProgressEmitter doubleProgress(Double total) {
    return new DefaultDoubleProgressEmitter(new ProgressChannel(transport, progressToken, total));
  }

  @Override
  public LongProgressEmitter longProgress(Long total) {
    return new DefaultLongProgressEmitter(new ProgressChannel(transport, progressToken, total));
  }

  @Override
  public CountingProgressEmitter countingProgress(Long total) {
    return new DefaultCountingProgressEmitter(new ProgressChannel(transport, progressToken, total));
  }

  @Override
  public PercentageCompleteProgressEmitter percentProgress() {
    return new DefaultPercentageCompleteProgressEmitter(
        new ProgressChannel(transport, progressToken, 1.0));
  }
}
