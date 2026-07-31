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
package com.callibrity.mocapi.examples.apps;

import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the example's handler beans before mocapi scans for handlers (mirrors the other example
 * apps). Ordering {@code before} {@link MocapiServerAutoConfiguration} guarantees {@link
 * GetTimeApp} exists when mocapi's handler discovery runs.
 */
@AutoConfiguration(before = MocapiServerAutoConfiguration.class)
public class AppsExampleConfiguration {

  @Bean
  public GetTimeApp getTimeApp() {
    return new GetTimeApp();
  }
}
