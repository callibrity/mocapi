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
package com.callibrity.mocapi.server.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.EmptyResult;
import com.callibrity.mocapi.server.session.McpSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpLifecycleServiceTest {

  @Mock private McpSessionService sessionService;

  @Test
  void cancelledWithNullParamsDoesNotThrow() {
    var service = new McpLifecycleService(sessionService);

    EmptyResult result = service.cancelled(null);

    assertThat(result).isSameAs(EmptyResult.INSTANCE);
  }
}
