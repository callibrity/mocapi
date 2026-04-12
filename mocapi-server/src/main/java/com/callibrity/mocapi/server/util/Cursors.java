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

import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Cursor-based pagination helper for MCP list methods. Cursors are Base64-encoded integer offsets.
 */
public final class Cursors {

  private Cursors() {}

  /**
   * Paginates a list and constructs the result directly via the provided function.
   *
   * @param all the full list of items
   * @param params the request params containing an optional cursor
   * @param pageSize the maximum number of items per page
   * @param resultCtor constructs the result from (items, nextCursor) — typically a record
   *     constructor reference like {@code ListToolsResult::new}
   * @return the constructed result
   */
  public static <T, R> R paginate(
      List<T> all,
      PaginatedRequestParams params,
      int pageSize,
      BiFunction<List<T>, String, R> resultCtor) {
    String cursor = params == null ? null : params.cursor();
    int offset = Math.clamp(decodeCursor(cursor), 0, all.size());
    int end = Math.min(offset + pageSize, all.size());
    List<T> page = List.copyOf(all.subList(offset, end));
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return resultCtor.apply(page, nextCursor);
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
}
