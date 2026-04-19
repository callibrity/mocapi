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
package com.callibrity.mocapi.server.autoconfigure;

import org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;

/**
 * Ordering-only auto-configuration that forces {@link SubstrateAutoConfiguration} to run after
 * {@code JacksonCodecAutoConfiguration}. Substrate's factory beans ({@code AtomFactory}, {@code
 * MailboxFactory}, etc.) use {@code @ConditionalOnBean(CodecFactory.class)}, so the {@code
 * CodecFactory} must be created first. The {@code codec-jackson} module is a runtime (not compile)
 * dependency, so the after-class is referenced by name.
 */
@AutoConfiguration(before = SubstrateAutoConfiguration.class)
@AutoConfigureAfter(name = "org.jwcarman.codec.jackson.JacksonCodecAutoConfiguration")
public class SubstrateOrderingAutoConfiguration {}
