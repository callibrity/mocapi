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
package com.callibrity.mocapi.examples;

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.examples.tools.CountdownTool;
import com.callibrity.mocapi.examples.tools.HelloTool;
import com.callibrity.mocapi.examples.tools.Rot13Tool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Shared auto-configuration for mocapi example applications. Registers the example tool beans so
 * every backend example (in-memory, redis, hazelcast, postgresql, ...) exposes the same set of
 * tools simply by depending on this module.
 *
 * <p>Registered before {@link MocapiAutoConfiguration} so that the example tools are present when
 * mocapi's tools registry scans for {@code @ToolService} beans.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class)
public class ExampleAutoConfiguration {

  @Bean
  public HelloTool helloTool() {
    return new HelloTool();
  }

  @Bean
  public Rot13Tool rot13Tool() {
    return new Rot13Tool();
  }

  @Bean
  public CountdownTool countdownTool() {
    return new CountdownTool();
  }
}
