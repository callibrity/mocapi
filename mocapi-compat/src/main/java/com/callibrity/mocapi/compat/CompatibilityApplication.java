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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CompatibilityApplication {

  public static void main(String[] args) {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    String encoded = Base64.getEncoder().encodeToString(key);

    SpringApplication app = new SpringApplication(CompatibilityApplication.class);
    app.setDefaultProperties(Map.of("mocapi.session-encryption-master-key", encoded));
    app.run(args);
  }
}
