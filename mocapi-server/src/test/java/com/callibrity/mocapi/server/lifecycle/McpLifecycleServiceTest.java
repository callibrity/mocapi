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
package com.callibrity.mocapi.server.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CancelledNotificationParams;
import com.callibrity.mocapi.model.EmptyResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpLifecycleServiceTest {

  private final McpLifecycleService service = new McpLifecycleService();

  @Test
  void
      a_cancellation_naming_the_original_request_id_is_acknowledged_without_reading_its_id_unsafely() {
    // requestId became optional in 2026-07-28; the handler must be able to log and acknowledge a
    // cancellation that DOES carry one without any NPE on the non-null path.
    var params = new CancelledNotificationParams(42, "client timed out", null);

    EmptyResult result = service.cancelled(params);

    assertThat(result).isSameAs(EmptyResult.INSTANCE);
  }

  @Test
  void a_cancellation_with_no_params_at_all_is_still_acknowledged_instead_of_throwing() {
    // The spec allows an empty-params cancellation; the handler must not NPE dereferencing params.
    EmptyResult result = service.cancelled(null);

    assertThat(result).isSameAs(EmptyResult.INSTANCE);
  }
}
