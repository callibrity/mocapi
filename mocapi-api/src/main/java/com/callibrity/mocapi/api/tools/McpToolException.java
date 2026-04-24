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
package com.callibrity.mocapi.api.tools;

import com.callibrity.mocapi.model.ContentBlock;
import java.util.List;

/**
 * Thrown by a {@code @McpTool} method to signal a tool-execution failure that should be returned to
 * the caller as a {@code CallToolResult} with {@code isError: true}. Use this instead of a bare
 * {@link RuntimeException} when you want the error to carry structured data or additional content
 * blocks beyond a plain message.
 *
 * <p>Per the MCP specification, tool-execution errors are <em>not</em> JSON-RPC errors — they're
 * ordinary results whose {@code isError} flag tells the calling LLM the tool failed. The spec gives
 * you exactly two knobs for the shape of that result:
 *
 * <ul>
 *   <li>{@link #getStructuredContent() structuredContent} — an optional object-shaped payload with
 *       additional machine-readable detail. Set via the {@code structuredContent} constructor
 *       parameter (a POJO/record that will be serialized via Jackson) or by overriding {@link
 *       #getStructuredContent()} in a subclass. Whatever you hand over must serialize to a JSON
 *       object; if Jackson produces anything else, mocapi fails the request with {@link
 *       IllegalStateException} rather than silently dropping {@code structuredContent}.
 *   <li>{@link #getAdditionalContent() additionalContent} — extra {@link ContentBlock}s to append
 *       to the result's {@code content} array after the automatically-generated message text block.
 *       Override in a subclass to supply extra content, such as images or embedded resources that
 *       describe the failure.
 * </ul>
 *
 * <p>Note: the MCP spec does not provide a dedicated "error code" slot on {@code CallToolResult} —
 * the only machine-readable signal is {@code isError}, supplemented by whatever you choose to put
 * in {@code structuredContent}. If you need to communicate an error code or category, put it in
 * {@code structuredContent}.
 *
 * <p><b>Subclassing.</b> Tool authors are encouraged to define domain-specific subclasses (e.g.
 * {@code UserNotFoundException extends McpToolException}) with a fixed message prefix and
 * structured payload shape. Mocapi catches the parent type, so every subclass is handled uniformly.
 * Override {@link #getStructuredContent()} and/or {@link #getAdditionalContent()} to customize the
 * result shape without having to plumb values through the constructor chain.
 *
 * <p><b>Async.</b> An {@code McpToolException} that fails a {@code CompletionStage} returned by an
 * async tool is handled the same way as one thrown synchronously — the await interceptor unwraps
 * the JDK's {@code CompletionException} so the original exception type reaches the error-mapping
 * path.
 */
public class McpToolException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  // transient because the payload is an arbitrary author-supplied Object — requiring it to be
  // Serializable would force every error POJO to implement Serializable, which is onerous and
  // not worth it (MCP tool-error transport is JSON, not Java serialization). On the rare paths
  // where an exception is actually Java-serialized (distributed runtimes, caches), the payload
  // is lost on deserialization; that's an acceptable trade-off.
  private final transient Object structuredContent;

  public McpToolException(String message) {
    this(message, null, null);
  }

  public McpToolException(String message, Throwable cause) {
    this(message, null, cause);
  }

  public McpToolException(String message, Object structuredContent) {
    this(message, structuredContent, null);
  }

  public McpToolException(String message, Object structuredContent, Throwable cause) {
    super(message, cause);
    this.structuredContent = structuredContent;
  }

  /**
   * Structured, machine-readable error payload attached to the resulting {@code CallToolResult}'s
   * {@code structuredContent} field. May be any POJO/record — mocapi runs it through Jackson at
   * catch time. The serialized form must be a JSON object; a non-object serialization (string,
   * array, primitive) causes the request to fail with {@link IllegalStateException}.
   *
   * <p>Default implementation returns the value passed to the constructor. Subclasses may override
   * to compute the payload from instance state (e.g. a {@code code} field plus a {@code details}
   * record) without threading the value through {@code super(...)}.
   *
   * @return structured error payload, or {@code null} if no structured content should be sent
   */
  public Object getStructuredContent() {
    return structuredContent;
  }

  /**
   * Extra content blocks to append to the result after the automatically-generated message text
   * block. Default implementation returns an empty list. Override in a subclass to supply
   * additional content — for example, a resource block pointing to a troubleshooting page, or an
   * image visualizing the failure state.
   *
   * @return zero-or-more content blocks; must not be {@code null}
   */
  public List<ContentBlock> getAdditionalContent() {
    return List.of();
  }
}
