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

/**
 * Maps a tool method's raw return value to a {@link CallToolResult}. One implementation is chosen
 * per handler at registration time by {@link ToolReturnTypeClassifier}, driven purely by the
 * method's declared return type — no runtime type inspection.
 *
 * <p>The four permitted implementations correspond to the four permitted tool return shapes:
 *
 * <ul>
 *   <li>{@link VoidResultMapper} for {@code void} / {@code Void}
 *   <li>{@link PassthroughResultMapper} for {@link CallToolResult}
 *   <li>{@link TextContentResultMapper} for {@link CharSequence} (ergonomic text-only shortcut)
 *   <li>{@link StructuredResultMapper} for a POJO/record whose derived JSON schema is an object
 * </ul>
 *
 * <p>For async tools ({@code CompletionStage<X>}), the await interceptor unwraps the future before
 * the mapper sees the value, so the mapper is selected based on {@code X}.
 */
public sealed interface ResultMapper
    permits VoidResultMapper,
        PassthroughResultMapper,
        TextContentResultMapper,
        StructuredResultMapper {

  CallToolResult map(Object result);
}
