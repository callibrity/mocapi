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
package com.callibrity.mocapi.server.prompts;

import static java.util.Optional.ofNullable;

import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.ListPromptsResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.server.ServerCapabilitiesBuilder;
import com.callibrity.mocapi.server.ServerCapabilitiesContributor;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Manages prompt registration, lookup, pagination, and JSON-RPC dispatch. */
@JsonRpcService
public class McpPromptsService implements ServerCapabilitiesContributor {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, McpPrompt> prompts;
  private final List<Prompt> sortedDescriptors;
  private final int pageSize;

  public McpPromptsService(List<McpPromptProvider> promptProviders) {
    this(promptProviders, DEFAULT_PAGE_SIZE);
  }

  public McpPromptsService(List<McpPromptProvider> promptProviders, int pageSize) {
    var allPrompts =
        promptProviders.stream().flatMap(provider -> provider.getMcpPrompts().stream()).toList();
    this.prompts =
        allPrompts.stream().collect(Collectors.toMap(p -> p.descriptor().name(), p -> p));
    this.sortedDescriptors =
        allPrompts.stream()
            .map(McpPrompt::descriptor)
            .sorted(Comparator.comparing(Prompt::name))
            .toList();
    this.pageSize = pageSize;
  }

  @JsonRpcMethod(McpMethods.PROMPTS_LIST)
  public ListPromptsResult listPrompts(@JsonRpcParams PaginatedRequestParams params) {
    var page = paginate(sortedDescriptors, params);
    return new ListPromptsResult(page.items(), page.nextCursor());
  }

  @JsonRpcMethod(McpMethods.PROMPTS_GET)
  public GetPromptResult getPrompt(@JsonRpcParams GetPromptRequestParams params) {
    String name = params.name();
    McpPrompt prompt = lookup(name);
    Map<String, String> arguments = params.arguments() != null ? params.arguments() : Map.of();
    return prompt.get(arguments);
  }

  public McpPrompt lookup(String name) {
    return ofNullable(prompts.get(name))
        .orElseThrow(
            () ->
                new JsonRpcException(
                    JsonRpcProtocol.INVALID_PARAMS, String.format("Prompt %s not found.", name)));
  }

  public boolean isEmpty() {
    return prompts.isEmpty();
  }

  @Override
  public void contribute(ServerCapabilitiesBuilder builder) {
    if (!isEmpty()) {
      builder.prompts(new PromptsCapability(false));
    }
  }

  private <T> Page<T> paginate(List<T> all, PaginatedRequestParams params) {
    String cursor = params == null ? null : params.cursor();
    int offset = Math.clamp(decodeCursor(cursor), 0, all.size());
    int end = Math.min(offset + pageSize, all.size());
    List<T> page = List.copyOf(all.subList(offset, end));
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new Page<>(page, nextCursor);
  }

  private static String encodeCursor(int offset) {
    return Base64.getEncoder().encodeToString(ByteBuffer.allocate(4).putInt(offset).array());
  }

  private static int decodeCursor(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return ByteBuffer.wrap(Base64.getDecoder().decode(cursor)).getInt();
    } catch (Exception _) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
  }

  private record Page<T>(List<T> items, String nextCursor) {}
}
