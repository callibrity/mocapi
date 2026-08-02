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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the {@code io.modelcontextprotocol/tasks} extension.
 *
 * @param defaultTtl fallback task time-to-live for {@code @McpTask} handlers that leave {@code ttl}
 *     blank
 * @param defaultPollInterval fallback poll interval for {@code @McpTask} handlers that leave {@code
 *     pollInterval} blank
 * @param sweepInterval how often the default in-memory {@code TaskStore} sweeps expired task
 *     records
 */
@ConfigurationProperties(prefix = "mocapi.tasks")
public record MocapiTasksProperties(
    @DefaultValue("PT1H") Duration defaultTtl,
    @DefaultValue("PT2S") Duration defaultPollInterval,
    @DefaultValue("PT1M") Duration sweepInterval) {}
