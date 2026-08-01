/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.resources;

import com.callibrity.mocapi.model.ReadResourceResult;
import java.util.Map;

/**
 * Produces the {@link ReadResourceResult} for a templated resource on {@code resources/read}, given
 * the concrete matched request {@code uri} and the path variables extracted from it (ADR-0035).
 * Templated analogue of {@link ResourceReader}; the reflective, annotation-scanned form wraps the
 * method's {@code MethodInvoker}. The {@code uri} is what a convenience return value (a bare {@code
 * String}, {@code byte[]}, {@code Resource}, …) is wrapped against, since a template has no fixed
 * URI of its own.
 */
@FunctionalInterface
public interface ResourceTemplateReader {

  ReadResourceResult read(String uri, Map<String, String> variables);
}
