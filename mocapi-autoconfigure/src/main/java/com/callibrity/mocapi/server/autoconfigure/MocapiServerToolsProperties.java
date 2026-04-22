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

import com.github.victools.jsonschema.generator.SchemaVersion;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mocapi.tools")
@Data
class MocapiServerToolsProperties {
  private SchemaVersion schemaVersion;

  /**
   * Opt-in dev/test guardrail: when {@code true}, every tool's return value is validated against
   * its declared output schema on each call and a schema mismatch fails loudly with a JSON-RPC
   * internal error. Defaults to {@code false} so production dispatch pays no validation cost; flip
   * it on in {@code @SpringBootTest} runs to catch drift between declared output schemas and actual
   * tool responses.
   */
  private boolean validateOutput;
}
