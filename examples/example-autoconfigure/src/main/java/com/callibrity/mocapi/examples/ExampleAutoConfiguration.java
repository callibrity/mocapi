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
package com.callibrity.mocapi.examples;

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.examples.tools.CountdownTool;
import com.callibrity.mocapi.examples.tools.HelloTool;
import com.callibrity.mocapi.examples.tools.Rot13Tool;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Shared auto-configuration for mocapi example applications. Registers the example tool beans and,
 * via the nested {@link EphemeralMasterKey} {@link EnvironmentPostProcessor}, generates a random
 * session encryption master key on startup if none is configured.
 *
 * <p>Registered before {@link MocapiAutoConfiguration} so that the example tools are present when
 * mocapi's tools registry scans for {@code @ToolService} beans.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class)
public class ExampleAutoConfiguration {

  @Bean
  public HelloTool helloTool() {
    return new HelloTool();
  }

  @Bean
  public Rot13Tool rot13Tool() {
    return new Rot13Tool();
  }

  @Bean
  public CountdownTool countdownTool() {
    return new CountdownTool();
  }

  /**
   * Generates an ephemeral 32-byte session encryption master key on application startup and
   * publishes it as the {@code mocapi.session-encryption-master-key} property if no value is
   * already configured.
   *
   * <p>Runs as an {@link EnvironmentPostProcessor} because {@link
   * com.callibrity.mocapi.MocapiProperties} binding happens before any {@code @AutoConfiguration}
   * class executes — a {@code @Bean} method or {@code @PostConstruct} would fire too late.
   *
   * <p>Any explicit value (env var, system property, configured property source) is respected.
   * When a key is generated, a WARN-level log line explains what happened and how to opt out, so
   * nobody accidentally ships this in production.
   */
  public static class EphemeralMasterKey implements EnvironmentPostProcessor {

    private static final Log log = LogFactory.getLog(EphemeralMasterKey.class);
    private static final String PROPERTY = "mocapi.session-encryption-master-key";
    private static final String SOURCE_NAME = "mocapiExampleEphemeralMasterKey";
    private static final int KEY_BYTES = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
      if (environment.containsProperty(PROPERTY)) {
        return;
      }
      byte[] key = new byte[KEY_BYTES];
      new SecureRandom().nextBytes(key);
      String encoded = Base64.getEncoder().encodeToString(key);
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource(SOURCE_NAME, Map.of(PROPERTY, encoded)));
      log.warn(
          "mocapi example: generated an ephemeral session encryption master key. "
              + "Sessions will not survive restart and will not work across nodes. "
              + "Set "
              + PROPERTY
              + " explicitly for persistent or clustered deployments.");
    }
  }
}
