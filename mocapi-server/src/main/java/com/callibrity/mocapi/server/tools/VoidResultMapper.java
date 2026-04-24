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
 * Mapper for tools that declare {@code void} or {@code Void} as their return type. Ignores the
 * (necessarily null) return value and produces a {@link CallToolResult} with an empty {@code
 * content} array — honestly conveying "nothing to report" rather than fabricating a dummy text
 * block, while still satisfying the MCP spec's requirement that {@code content} be present.
 */
// Sonar java:S6548 flags the classic-singleton pattern. Intentional here: the mapper is
// stateless, paired with every void-returning tool handler, and the "one canonical INSTANCE"
// shape matches the other mappers in the sealed ResultMapper hierarchy.
@SuppressWarnings("java:S6548")
public final class VoidResultMapper implements ResultMapper {

  public static final VoidResultMapper INSTANCE = new VoidResultMapper();

  private VoidResultMapper() {}

  @Override
  public CallToolResult map(Object result) {
    return new CallToolResult(List.of(), null, null);
  }
}
