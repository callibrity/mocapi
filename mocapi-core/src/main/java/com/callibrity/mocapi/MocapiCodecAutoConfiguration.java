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
package com.callibrity.mocapi;

import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a default {@link CodecFactory} bean backed by Jackson so that substrate's {@code
 * SubstrateAutoConfiguration} can assemble the {@code AtomFactory}, {@code JournalFactory}, and
 * {@code MailboxFactory} beans (which all have {@code @ConditionalOnBean(CodecFactory.class)}).
 *
 * <p>This auto-config is explicitly ordered {@code before = SubstrateAutoConfiguration.class}
 * because substrate's own @Bean methods check for a {@code CodecFactory} bean at the moment their
 * {@code @ConditionalOnBean} is evaluated — if the bean is registered later in the processing
 * order, substrate silently skips building its factory beans. Mocapi always wants those factories
 * to exist (that's how mocapi's session store, streaming, and elicitation work), so mocapi owns the
 * responsibility of ensuring a {@code CodecFactory} is present before substrate looks for one.
 *
 * <p>If the application already provides its own {@code CodecFactory} bean (e.g., because the user
 * wired a custom one or because {@code codec-jackson} beat this module to it), the
 * {@code @ConditionalOnMissingBean} guard steps aside.
 */
@AutoConfiguration(before = SubstrateAutoConfiguration.class)
public class MocapiCodecAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(CodecFactory.class)
  public CodecFactory codecFactory(ObjectMapper objectMapper) {
    return new JacksonCodecFactory(objectMapper);
  }
}
