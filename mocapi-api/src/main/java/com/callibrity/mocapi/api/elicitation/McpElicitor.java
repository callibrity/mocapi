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
package com.callibrity.mocapi.api.elicitation;

import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import java.util.function.Consumer;

/**
 * Elicits structured input from the user mid-handler (ADR-0024). Available from tool, prompt, and
 * resource handlers — either declare an {@code McpElicitor} parameter, or (in tools) use the
 * inherited {@code McpToolContext.elicit(...)}.
 *
 * <p>Elicitation works by multi-round-trip replay (ADR-0021): when no answer is available yet, the
 * handler's stack unwinds and the client receives an {@code InputRequiredResult}; on retry the
 * handler re-executes from the top and answered {@code elicit} calls return immediately. <b>Code
 * before your last {@code elicit} call runs once per round trip</b> — keep it idempotent or move
 * side effects after the final elicitation (see the interactive-tools guide).
 *
 * <p>Calling {@code elicit} when the client did not declare the {@code elicitation} capability
 * throws {@link McpElicitationNotSupportedException}, surfaced on the wire as the spec's {@code
 * -32003 MissingRequiredClientCapabilityError}.
 */
public interface McpElicitor {

  ScopedValue<McpElicitor> CURRENT = ScopedValue.newInstance();

  /**
   * Elicits user input described by the given form-mode request.
   *
   * @param params the elicitation request parameters
   * @return the user's response
   */
  ElicitResult elicit(ElicitRequestFormParams params);

  /**
   * Elicits user input using a schema assembled by the given customizer.
   *
   * @param message the human-readable prompt shown to the user
   * @param schemaCustomizer receives the flat-object schema builder (ADR-0015)
   * @return the user's response
   */
  default ElicitResult elicit(String message, Consumer<RequestedSchemaBuilder> schemaCustomizer) {
    var builder = new RequestedSchemaBuilder();
    schemaCustomizer.accept(builder);
    var params = new ElicitRequestFormParams(message, builder.build());
    return elicit(params);
  }
}
