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
package com.callibrity.mocapi.server.util;

import static java.util.Optional.ofNullable;

import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base class for MCP services that manage a named collection with cursor-based pagination and
 * lookup-by-name.
 *
 * @param <T> the item type (e.g., McpTool, McpPrompt)
 * @param <D> the descriptor type (e.g., Tool, Prompt)
 */
public abstract class PaginatedService<T, D> {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, T> items;
  private final List<D> sortedDescriptors;
  private final int pageSize;
  private final String entityName;

  protected PaginatedService(
      List<T> allItems,
      Function<T, String> keyExtractor,
      Function<T, D> descriptorExtractor,
      Comparator<D> comparator,
      String entityName,
      int pageSize) {
    this.items = allItems.stream().collect(Collectors.toMap(keyExtractor, t -> t));
    this.sortedDescriptors = allItems.stream().map(descriptorExtractor).sorted(comparator).toList();
    this.entityName = entityName;
    this.pageSize = pageSize;
  }

  public T lookup(String name) {
    return ofNullable(items.get(name))
        .orElseThrow(
            () ->
                new JsonRpcException(
                    JsonRpcProtocol.INVALID_PARAMS,
                    String.format("%s %s not found.", entityName, name)));
  }

  protected <R> R paginate(
      PaginatedRequestParams params, BiFunction<List<D>, String, R> resultCtor) {
    return Cursors.paginate(sortedDescriptors, params, pageSize, resultCtor);
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }
}
