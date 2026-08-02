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
package com.callibrity.mocapi.server.autoconfigure;

import com.callibrity.mocapi.server.discover.ServerCapabilitiesCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Warns once at startup when {@link ServerCapabilitiesCustomizer} beans exist but were silently
 * discarded because the application supplied its own {@code ServerCapabilities} bean.
 *
 * <p>{@link MocapiServerAutoConfiguration#mcpServerCapabilities} is the only producer that applies
 * customizers; when a deployment defines its own {@code ServerCapabilities} bean,
 * {@code @ConditionalOnMissingBean} backs that factory method off entirely and no customizer ever
 * runs. That is a documented, intentional override ({@link ServerCapabilitiesCustomizer}'s
 * javadoc), but a deployment that adds a customizer bean without realizing its own {@code
 * ServerCapabilities} bean shadows it is easy to get wrong silently — this auditor turns the silent
 * no-op into a single WARN naming the discarded beans.
 *
 * <p>Detection: the factory method's bean name ({@link #FACTORY_BEAN_NAME}) is only present in the
 * bean factory when {@code @ConditionalOnMissingBean} let it through. Its absence, combined with at
 * least one {@link ServerCapabilitiesCustomizer} bean, is exactly the discarded-customizer case.
 *
 * <p>This bean is registered unconditionally (see {@link
 * MocapiServerAutoConfiguration#mcpServerCapabilitiesOverrideAuditor}'s javadoc for why it must not
 * be gated on {@code ServerCapabilitiesCustomizer} beans existing at auto-configuration processing
 * time) and defers every lookup — both the factory-bean-name check and the customizer scan — to
 * {@link #afterSingletonsInstantiated()}. By then every singleton, from every auto-configuration
 * regardless of processing order, is guaranteed to exist, so the detection is immune to which
 * auto-configuration happens to run first.
 */
public class ServerCapabilitiesOverrideAuditor implements SmartInitializingSingleton {

  /**
   * The bean name Spring derives from {@link MocapiServerAutoConfiguration#mcpServerCapabilities}.
   * Present in the bean factory only when our {@code @ConditionalOnMissingBean} factory won.
   */
  static final String FACTORY_BEAN_NAME = "mcpServerCapabilities";

  private static final Logger log =
      LoggerFactory.getLogger(ServerCapabilitiesOverrideAuditor.class);

  private final ConfigurableListableBeanFactory beanFactory;

  public ServerCapabilitiesOverrideAuditor(ConfigurableListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (beanFactory.containsBeanDefinition(FACTORY_BEAN_NAME)) {
      return; // our factory produced the bean; customizers were applied, nothing discarded
    }
    String[] customizerNames = beanFactory.getBeanNamesForType(ServerCapabilitiesCustomizer.class);
    if (customizerNames.length == 0) {
      return;
    }
    log.warn(
        "A user-supplied ServerCapabilities bean replaced mocapi's default; the following "
            + "ServerCapabilitiesCustomizer bean(s) were never applied and are being discarded: {}",
        String.join(", ", customizerNames));
  }
}
