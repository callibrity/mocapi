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
package com.callibrity.mocapi.compat;

import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@SpringBootApplication
public class CompatApplication {

  // Workaround: Jackson3AutoConfiguration's @ConditionalOnBean(ObjectMapper.class) fails due to
  // auto-configuration ordering — it runs before JacksonAutoConfiguration creates the ObjectMapper.
  // This explicit bean registration ensures tools/call parameter extraction works correctly.
  @Bean
  Jackson3ParameterResolver jackson3ParameterResolver(ObjectMapper mapper) {
    return new Jackson3ParameterResolver(mapper);
  }
}
