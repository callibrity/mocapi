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
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AppsUiDefaultsTest {

  @Test
  void a_config_bean_that_never_sets_any_fallback_list_does_not_npe_and_widens_no_policy() {
    // If the null-coalescing in the compact constructor were dropped, an operator who never
    // configures mocapi.apps.* would crash the autoconfigure module at startup (NPE) instead of
    // getting "no fallbacks" — every CSP directive and the sandbox token list must resolve to an
    // empty, restrictive list, never null and never a widened default.
    var defaults = new AppsUiDefaults(null, null, null, null, null);

    assertThat(defaults.cspConnect()).isEmpty();
    assertThat(defaults.cspResource()).isEmpty();
    assertThat(defaults.cspFrame()).isEmpty();
    assertThat(defaults.cspBaseUri()).isEmpty();
    assertThat(defaults.sandbox()).isEmpty();
  }

  @Test
  void a_configured_connect_domain_is_not_silently_dropped_by_the_null_fallback() {
    // The inverse failure mode: if the null-check ever became unconditional (always substituting
    // List.of()), an operator's configured connect-src allowlist would be silently discarded and
    // every UI resource would fall back to "no connect fallback" — a policy regression which the
    // caller would not notice.
    var defaults = new AppsUiDefaults(List.of("https://api.example.com"), null, null, null, null);

    assertThat(defaults.cspConnect()).containsExactly("https://api.example.com");
    assertThat(defaults.cspResource()).isEmpty();
  }

  @Test
  void none_produces_every_list_empty() {
    var defaults = AppsUiDefaults.none();

    assertThat(defaults.cspConnect()).isEmpty();
    assertThat(defaults.cspResource()).isEmpty();
    assertThat(defaults.cspFrame()).isEmpty();
    assertThat(defaults.cspBaseUri()).isEmpty();
    assertThat(defaults.sandbox()).isEmpty();
  }

  @Test
  void
      lists_are_defensively_copied_so_the_caller_cannot_mutate_a_shared_fallback_after_construction() {
    var mutable = new ArrayList<String>();
    mutable.add("https://api.example.com");
    var defaults = new AppsUiDefaults(mutable, null, null, null, null);

    mutable.add("https://evil.example.com");

    assertThat(defaults.cspConnect()).containsExactly("https://api.example.com");
  }
}
