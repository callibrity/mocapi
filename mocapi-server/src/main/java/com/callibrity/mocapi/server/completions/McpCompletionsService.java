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
package com.callibrity.mocapi.server.completions;

import com.callibrity.mocapi.model.CompleteRequestParams;
import com.callibrity.mocapi.model.CompleteResult;
import com.callibrity.mocapi.model.Completion;
import com.callibrity.mocapi.model.CompletionArgument;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.PromptReference;
import com.callibrity.mocapi.model.ResourceTemplateReference;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Strings;

/**
 * Registry and JSON-RPC handler for MCP {@code completion/complete}. Entries are pushed in at
 * registration time by the annotation-based prompt and resource-template providers — one entry per
 * enum-typed argument (or {@link io.swagger.v3.oas.annotations.media.Schema} {@code
 * allowableValues}). Clients asking for completions get a prefix-filtered slice of the registered
 * values, capped at {@link #MAX_VALUES} per response.
 *
 * <p>Non-enum arguments, unknown prompt names, and unknown resource templates all produce an empty
 * result — consistent with the MCP spec's "best effort" wording.
 */
@JsonRpcService
public class McpCompletionsService {

  /**
   * Maximum number of completion values returned in a single response. Matches the soft cap
   * suggested by the MCP spec and keeps SSE frames bounded.
   */
  public static final int MAX_VALUES = 100;

  private final Map<String, Map<String, List<String>>> promptArguments = new HashMap<>();
  private final Map<String, Map<String, List<String>>> resourceTemplateVariables = new HashMap<>();

  /**
   * Registers the set of completion values for a prompt argument. Called by the annotation-based
   * prompt provider for each parameter whose type resolves to an enum or whose {@code @Schema}
   * annotation supplies {@code allowableValues}.
   */
  public void registerPromptArgument(String promptName, String argumentName, List<String> values) {
    promptArguments
        .computeIfAbsent(promptName, k -> new HashMap<>())
        .put(argumentName, List.copyOf(values));
  }

  /**
   * Registers the set of completion values for a resource-template URI variable. Symmetric to
   * {@link #registerPromptArgument} but keyed on the raw {@code uriTemplate} string.
   */
  public void registerResourceTemplateVariable(
      String uriTemplate, String variableName, List<String> values) {
    resourceTemplateVariables
        .computeIfAbsent(uriTemplate, k -> new HashMap<>())
        .put(variableName, List.copyOf(values));
  }

  @JsonRpcMethod(McpMethods.COMPLETION_COMPLETE)
  public CompleteResult complete(@JsonRpcParams CompleteRequestParams params) {
    List<String> candidates = candidatesFor(params);
    List<String> filtered = filterAndCap(candidates, params.argument());
    return new CompleteResult(new Completion(filtered, filtered.size(), false));
  }

  private List<String> candidatesFor(CompleteRequestParams params) {
    return switch (params.ref()) {
      case PromptReference pr ->
          promptArguments
              .getOrDefault(pr.name(), Map.of())
              .getOrDefault(params.argument().name(), List.of());
      case ResourceTemplateReference rtr ->
          resourceTemplateVariables
              .getOrDefault(rtr.uri(), Map.of())
              .getOrDefault(params.argument().name(), List.of());
    };
  }

  private static List<String> filterAndCap(List<String> candidates, CompletionArgument argument) {
    String prefix = argument == null || argument.value() == null ? "" : argument.value();
    List<String> matches = new ArrayList<>();
    for (String candidate : candidates) {
      if (Strings.CI.startsWith(candidate, prefix)) {
        matches.add(candidate);
        if (matches.size() >= MAX_VALUES) break;
      }
    }
    return Collections.unmodifiableList(matches);
  }
}
