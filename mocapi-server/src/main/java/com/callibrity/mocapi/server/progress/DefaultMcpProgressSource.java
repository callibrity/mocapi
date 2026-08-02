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
package com.callibrity.mocapi.server.progress;

import com.callibrity.mocapi.api.progress.CountingProgressEmitter;
import com.callibrity.mocapi.api.progress.DoubleProgressEmitter;
import com.callibrity.mocapi.api.progress.LongProgressEmitter;
import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.api.progress.PercentageCompleteProgressEmitter;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import java.util.Objects;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/**
 * Default {@link McpProgressSource} for one handler invocation (ADR-0025). Captures a {@link
 * ProgressSink} once; each factory call mints a fresh, independently-stateful emitter over a new
 * {@link ProgressChannel}. The {@code (McpTransport, ValueNode)} constructor builds the
 * transport-notification sink used in production; a {@code null} progress token or transport yields
 * a sink that validates but sends nothing.
 */
public class DefaultMcpProgressSource implements McpProgressSource {

  private final ProgressSink sink;

  public DefaultMcpProgressSource(ProgressSink sink) {
    this.sink = Objects.requireNonNull(sink);
  }

  public DefaultMcpProgressSource(McpTransport transport, ValueNode progressToken) {
    this(transportSink(transport, progressToken));
  }

  private static ProgressSink transportSink(McpTransport transport, ValueNode progressToken) {
    return (progress, total, message) -> {
      if (transport == null || progressToken == null) {
        return;
      }
      ObjectNode params = JsonNodeFactory.instance.objectNode();
      params.set("progressToken", progressToken);
      putNumber(params, "progress", progress);
      if (total != null) {
        putNumber(params, "total", total);
      }
      if (message != null) {
        params.put("message", message);
      }
      transport.send(new JsonRpcNotification("2.0", McpMethods.NOTIFICATIONS_PROGRESS, params));
    };
  }

  /**
   * Writes a numeric field preserving the emitter's chosen wire type: integral emitters (long,
   * counting) serialize whole-number JSON ({@code 5}), floating emitters (double, percent)
   * serialize floating-point JSON ({@code 5.0}). Both are valid per the spec's {@code number} type.
   */
  private static void putNumber(ObjectNode node, String field, Number value) {
    if (value instanceof Double || value instanceof Float) {
      node.put(field, value.doubleValue());
    } else {
      node.put(field, value.longValue());
    }
  }

  @Override
  public DoubleProgressEmitter doubleProgress(Double total) {
    return new DefaultDoubleProgressEmitter(new ProgressChannel(sink, total));
  }

  @Override
  public LongProgressEmitter longProgress(Long total) {
    return new DefaultLongProgressEmitter(new ProgressChannel(sink, total));
  }

  @Override
  public CountingProgressEmitter countingProgress(Long total) {
    return new DefaultCountingProgressEmitter(new ProgressChannel(sink, total));
  }

  @Override
  public PercentageCompleteProgressEmitter percentProgress() {
    return new DefaultPercentageCompleteProgressEmitter(new ProgressChannel(sink, 1.0));
  }
}
