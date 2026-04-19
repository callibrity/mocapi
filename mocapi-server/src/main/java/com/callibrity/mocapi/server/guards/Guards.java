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
package com.callibrity.mocapi.server.guards;

import java.util.List;

/**
 * Evaluates a handler's {@link Guard} list with AND semantics and short-circuit on first {@link
 * GuardDecision.Deny}. An empty list returns {@link GuardDecision.Allow}.
 */
public final class Guards {

  private static final GuardDecision ALLOW = new GuardDecision.Allow();

  private Guards() {}

  public static GuardDecision evaluate(List<Guard> guards) {
    for (Guard guard : guards) {
      GuardDecision decision = guard.check();
      if (decision instanceof GuardDecision.Deny) {
        return decision;
      }
    }
    return ALLOW;
  }

  /** Convenience: true iff {@link #evaluate(List)} returns an {@link GuardDecision.Allow}. */
  public static boolean allows(List<Guard> guards) {
    return evaluate(guards) instanceof GuardDecision.Allow;
  }
}
