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

import com.callibrity.mocapi.model.ListPromptsResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.util.Cursors;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PromptsRegistry {

  private final Map<String, McpPrompt> prompts;
  private final List<Prompt> sortedDescriptors;
  private final int pageSize;

  public PromptsRegistry(List<McpPrompt> prompts, int pageSize) {
    this.prompts =
        prompts.stream()
            .collect(
                Collectors.toMap(
                    p -> p.descriptor().name(),
                    p -> p,
                    (a, b) -> {
                      throw new IllegalArgumentException(
                          "Duplicate prompt name: " + a.descriptor().name());
                    }));
    this.sortedDescriptors =
        prompts.stream()
            .map(McpPrompt::descriptor)
            .sorted(Comparator.comparing(Prompt::name))
            .toList();
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

  public ListPromptsResult listPrompts(PaginatedRequestParams params) {
    var page = Cursors.paginate(sortedDescriptors, params, pageSize);
    return new ListPromptsResult(page.items(), page.nextCursor());
  }
}
