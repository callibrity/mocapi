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
package com.callibrity.mocapi.logging;

import com.callibrity.mocapi.server.session.McpSession;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration that registers {@link McpMdcInterceptor} as an ambient methodical interceptor.
 */
@AutoConfiguration
@ConditionalOnClass({MDC.class, MethodInterceptor.class, McpSession.class})
public class MocapiLoggingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpMdcInterceptor mcpMdcInterceptor() {
    return new McpMdcInterceptor();
  }
}
