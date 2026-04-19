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
 * Declares that the annotated MCP handler method requires the caller to hold at least one of the
 * listed Spring Security roles. Evaluated by {@link RoleGuard}: any single role grants access (OR
 * semantics), matching Spring Security's {@code hasAnyRole(...)} convention. Values may be given
 * bare ({@code "ADMIN"}) or prefixed ({@code "ROLE_ADMIN"}); both forms resolve to the same granted
 * authority.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRole {

  /** Roles any of which grants access (OR semantics). Must be non-empty. */
  String[] value();
}
