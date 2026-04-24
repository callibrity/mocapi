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
import com.callibrity.mocapi.model.TextContent;
import java.util.List;

/**
 * Mapper for tools that declare a {@link CharSequence} (including {@link String}) return type. The
 * returned value is {@code toString()}ed into a single text content block, with no structured
 * content — an ergonomic shortcut for tools that simply want to return a line of prose without
 * modeling a record.
 */
public final class TextContentResultMapper implements ResultMapper {

  public static final TextContentResultMapper INSTANCE = new TextContentResultMapper();

  private TextContentResultMapper() {}

  @Override
  public CallToolResult map(Object result) {
    String text = result == null ? "" : result.toString();
    return new CallToolResult(List.of(new TextContent(text, null)), null, null);
  }
}
