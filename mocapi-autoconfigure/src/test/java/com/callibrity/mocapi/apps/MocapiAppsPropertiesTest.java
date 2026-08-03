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

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiAppsPropertiesTest {

  @Test
  void
      an_application_that_never_configures_mocapi_apps_still_boots_with_a_safe_all_empty_fallback() {
    // If csp/sandbox null-handling regressed to a plain field assignment, any app that doesn't set
    // mocapi.apps.* at all (the common case) would NPE deep inside toAppsUiDefaults() the first
    // time an @McpAppResource with an empty csp/sandbox list needed a fallback, instead of falling
    // back to "no additional CSP/sandbox directives".
    var props = new MocapiAppsProperties(null, null);

    assertThat(props.csp().connect()).isEmpty();
    assertThat(props.csp().resource()).isEmpty();
    assertThat(props.csp().frame()).isEmpty();
    assertThat(props.csp().baseUri()).isEmpty();
    assertThat(props.sandbox()).isEmpty();
  }

  @Test
  void toAppsUiDefaults_carries_every_configured_list_through_to_the_resolved_defaults() {
    // The inverse risk: an operator sets mocapi.apps.csp.connect (and the other lists) expecting
    // every @McpAppResource with an empty csp/sandbox annotation to pick them up. If
    // toAppsUiDefaults() dropped or transposed a field, the operator's configured allowlist would
    // silently never apply.
    var csp =
        new MocapiAppsProperties.CspDefaults(
            List.of("https://connect.example.com"),
            List.of("https://resource.example.com"),
            List.of("https://frame.example.com"),
            List.of("https://base.example.com"));
    var props = new MocapiAppsProperties(csp, List.of("allow-scripts"));

    AppsUiDefaults defaults = props.toAppsUiDefaults();

    assertThat(defaults.cspConnect()).containsExactly("https://connect.example.com");
    assertThat(defaults.cspResource()).containsExactly("https://resource.example.com");
    assertThat(defaults.cspFrame()).containsExactly("https://frame.example.com");
    assertThat(defaults.cspBaseUri()).containsExactly("https://base.example.com");
    assertThat(defaults.sandbox()).containsExactly("allow-scripts");
  }

  @Test
  void
      a_csp_defaults_block_with_only_some_lists_configured_leaves_the_rest_empty_rather_than_null() {
    // Per-list independence is the point of ADR-0039: configuring one CSP directive must not force
    // the operator to also configure the others, and the unconfigured ones must be safely empty,
    // not null (which would NPE the first time a resource asked for its fallback).
    var csp =
        new MocapiAppsProperties.CspDefaults(
            List.of("https://connect.example.com"), null, null, null);

    assertThat(csp.connect()).containsExactly("https://connect.example.com");
    assertThat(csp.resource()).isEmpty();
    assertThat(csp.frame()).isEmpty();
    assertThat(csp.baseUri()).isEmpty();
  }
}
