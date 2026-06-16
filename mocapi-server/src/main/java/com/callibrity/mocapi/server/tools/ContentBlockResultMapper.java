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
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.ResultTypes;
import java.util.List;

/**
 * Mapper for tools that declare a single {@link ContentBlock} return type (e.g. {@link
 * com.callibrity.mocapi.model.ImageContent}, {@link com.callibrity.mocapi.model.AudioContent},
 * {@link com.callibrity.mocapi.model.ResourceLink}, {@link
 * com.callibrity.mocapi.model.EmbeddedResource}). The block becomes the sole item of the result's
 * {@code content} list, with no structured content — an ergonomic shortcut for returning one
 * non-text content item without hand-building a {@link CallToolResult}.
 */
// Sonar java:S6548 flags the classic-singleton pattern. Intentional here: the mapper is
// stateless and matches the "one canonical INSTANCE" shape of the other mappers in the sealed
// ResultMapper hierarchy.
@SuppressWarnings("java:S6548")
public final class ContentBlockResultMapper implements ResultMapper {

  public static final ContentBlockResultMapper INSTANCE = new ContentBlockResultMapper();

  private ContentBlockResultMapper() {}

  @Override
  public CallToolResult map(Object result) {
    if (result == null) {
      return new CallToolResult(List.of(), null, null, ResultTypes.COMPLETE);
    }
    return new CallToolResult(List.of((ContentBlock) result), null, null, ResultTypes.COMPLETE);
  }
}
