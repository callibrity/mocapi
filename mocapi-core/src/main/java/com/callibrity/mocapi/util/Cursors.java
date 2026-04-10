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
package com.callibrity.mocapi.util;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

public final class Cursors {

  private Cursors() {}

  public static String encode(int offset) {
    return Base64.getEncoder().encodeToString(ByteBuffer.allocate(4).putInt(offset).array());
  }

  public static int decode(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return ByteBuffer.wrap(Base64.getDecoder().decode(cursor)).getInt();
    } catch (Exception _) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  public static <T> Page<T> paginate(List<T> all, String cursor, int pageSize) {
    int offset = Math.clamp(decode(cursor), 0, all.size());
    int end = Math.min(offset + pageSize, all.size());
    List<T> page = List.copyOf(all.subList(offset, end));
    String nextCursor = end < all.size() ? encode(end) : null;
    return new Page<>(page, nextCursor);
  }
}
