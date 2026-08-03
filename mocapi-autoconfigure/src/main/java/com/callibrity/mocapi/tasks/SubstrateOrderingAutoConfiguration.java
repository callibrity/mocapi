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
package com.callibrity.mocapi.tasks;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Ordering-only auto-configuration that forces Substrate's {@code SubstrateAutoConfiguration} to
 * run after {@code codec-jackson}'s {@code JacksonCodecAutoConfiguration}. Substrate 0.8.0's
 * factory beans ({@code AtomFactory}, {@code Notifier}, etc.) use
 * {@code @ConditionalOnBean(CodecFactory.class)}, but Substrate declares no ordering against the
 * codec auto-configurations, so those conditions evaluate before the {@code CodecFactory} bean
 * registers and the whole chain silently backs off. Wedging this empty class between the two in the
 * sort graph heals the chain. Both references are by name: neither substrate-core nor codec-jackson
 * is a compile dependency of this module, and absent classes make the ordering a no-op, so this
 * class is harmless when Substrate is not on the classpath.
 *
 * <p>Resurrects the identical pre-2026-07-28 fix ({@code SubstrateOrderingAutoConfiguration} in the
 * old mocapi-protocol module). The proper fix belongs upstream (Substrate declaring
 * {@code @AutoConfigureAfter} on the codec auto-configurations) and is tracked for a future
 * Substrate release; this shim remains harmless once that lands.
 */
@AutoConfiguration(
    beforeName = "org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration",
    afterName = "org.jwcarman.codec.jackson.JacksonCodecAutoConfiguration")
public class SubstrateOrderingAutoConfiguration {}
