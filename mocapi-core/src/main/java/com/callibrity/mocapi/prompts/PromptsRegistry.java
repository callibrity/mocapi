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
package com.callibrity.mocapi.prompts;

import static java.util.Optional.ofNullable;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PromptsRegistry {

  private final Map<String, McpPrompt> prompts;
  private final int pageSize;

  public PromptsRegistry(List<McpPrompt> prompts, int pageSize) {
    this.prompts = prompts.stream().collect(Collectors.toMap(McpPrompt::name, p -> p));
    this.pageSize = pageSize;
  }

  public boolean isEmpty() {
    return prompts.isEmpty();
  }

  public McpPrompt lookup(String name) {
    return ofNullable(prompts.get(name))
        .orElseThrow(
            () ->
                new JsonRpcException(
                    JsonRpcProtocol.INVALID_PARAMS, String.format("Prompt not found: %s", name)));
  }

  public ListPromptsResponse listPrompts(String cursor) {
    List<McpPromptDescriptor> allDescriptors =
        prompts.values().stream()
            .map(
                p ->
                    new McpPromptDescriptor(
                        p.name(), p.title(), p.description(), p.icons(), p.arguments()))
            .sorted(Comparator.comparing(McpPromptDescriptor::name))
            .toList();
    return paginate(allDescriptors, cursor);
  }

  private ListPromptsResponse paginate(List<McpPromptDescriptor> all, String cursor) {
    int offset = decodeCursor(cursor);
    if (offset < 0 || offset > all.size()) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
    int end = Math.min(offset + pageSize, all.size());
    List<McpPromptDescriptor> page = List.copyOf(all.subList(offset, end));
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new ListPromptsResponse(page, nextCursor);
  }

  static String encodeCursor(int offset) {
    return Base64.getEncoder()
        .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
  }

  static int decodeCursor(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return Integer.parseInt(
          new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException _) {
      return -1;
    }
  }
}
