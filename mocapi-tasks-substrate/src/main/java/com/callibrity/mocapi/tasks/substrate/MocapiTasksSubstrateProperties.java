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
package com.callibrity.mocapi.tasks.substrate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Substrate-backed {@link com.callibrity.mocapi.tasks.store.TaskStore}.
 *
 * @param keyPrefix prefix applied to every backend atom key; defaults to {@code mocapi:tasks:}
 */
@ConfigurationProperties(prefix = "mocapi.tasks.substrate")
public record MocapiTasksSubstrateProperties(@DefaultValue("mocapi:tasks:") String keyPrefix) {}
