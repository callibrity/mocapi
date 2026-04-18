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

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Shared helpers for pulling String values out of mocapi annotations. Every annotation-processor
 * (tools, prompts, resources, resource templates) resolves Spring placeholders and then falls back
 * to a generated default when the resolved value is blank.
 */
public final class AnnotationStrings {

  private AnnotationStrings() {}

  /**
   * Resolves Spring placeholders in {@code raw} (e.g. {@code ${prop}} or {@code #{SpEL}}), trims
   * the result to null, and returns either the non-blank resolved value or the supplied fallback.
   */
  public static String resolveOrDefault(
      StringValueResolver resolver, String raw, Supplier<String> fallback) {
    return Optional.ofNullable(StringUtils.trimToNull(resolver.resolveStringValue(raw)))
        .orElseGet(fallback);
  }

  /**
   * Resolves Spring placeholders in {@code raw} and trims the result to null. Returns null if the
   * resolved value is blank. Use when the caller has no meaningful fallback (e.g. {@code mimeType}
   * is legitimately optional).
   */
  public static String resolveOrNull(StringValueResolver resolver, String raw) {
    return StringUtils.trimToNull(resolver.resolveStringValue(raw));
  }
}
