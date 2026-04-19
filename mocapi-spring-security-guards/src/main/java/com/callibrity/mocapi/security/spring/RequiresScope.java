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
package com.callibrity.mocapi.security.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated MCP handler method requires the caller to hold every listed OAuth2
 * scope. Evaluated by {@link ScopeGuard}: all scopes must be present on the current {@code
 * Authentication} (AND semantics), in the form of {@code SCOPE_<name>} granted authorities — the
 * canonical shape Spring Security's JWT / opaque-token converters produce. Denial hides the handler
 * from list operations and returns JSON-RPC {@code -32003} on invocation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresScope {

  /** Scopes the caller must hold (all required, AND semantics). Must be non-empty. */
  String[] value();
}
