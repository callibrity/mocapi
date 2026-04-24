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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.CallToolResult;
import java.util.List;

/**
 * Mapper for tools that declare {@link CallToolResult} as their return type. The author has opted
 * out of mocapi's auto-conversion and is hand-crafting the result, so the mapper simply returns the
 * value as-is. A {@code null} return is treated as an empty result — {@code content: []}, no
 * structured content — so the wire shape still satisfies the spec's required-{@code content}
 * constraint without fabricating a meaningless text block.
 */
public final class PassthroughResultMapper implements ResultMapper {

  public static final PassthroughResultMapper INSTANCE = new PassthroughResultMapper();

  private PassthroughResultMapper() {}

  @Override
  public CallToolResult map(Object result) {
    if (result == null) {
      return new CallToolResult(List.of(), null, null);
    }
    return (CallToolResult) result;
  }
}
