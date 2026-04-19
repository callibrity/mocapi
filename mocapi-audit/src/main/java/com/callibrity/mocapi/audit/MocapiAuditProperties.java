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
package com.callibrity.mocapi.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for {@code mocapi-audit}. */
@ConfigurationProperties("mocapi.audit")
@Data
public class MocapiAuditProperties {

  /**
   * When {@code true}, each audit event gains an {@code arguments_hash} field — SHA-256 of the
   * canonicalized argument payload. Off by default because even a hash of sensitive arguments can
   * be a weak fingerprint; opt in explicitly when you want call-correlation without recording
   * contents.
   */
  private boolean hashArguments = false;
}
