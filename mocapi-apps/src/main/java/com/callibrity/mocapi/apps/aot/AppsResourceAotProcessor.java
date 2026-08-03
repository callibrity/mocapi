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
package com.callibrity.mocapi.apps.aot;

import com.callibrity.mocapi.apps.McpUi;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Registers AOT resource-inclusion hints for every {@code @McpUi(resource = …)} classpath bundle
 * (ADR-0036, ADR-0039), so GraalVM native-image bundles the served HTML/JS into the image.
 *
 * <p>This is a distinct hint category from {@link AppsRuntimeHints}: that registrar covers Jackson
 * <em>reflection</em> for the {@code _meta.ui} wire records, but {@link
 * com.callibrity.mocapi.apps.AppUiResourceContributor} reads the bundle's raw bytes off the
 * classpath at bean-construction time via {@code ResourceLoader#getResource(String)} — GraalVM does
 * not bundle arbitrary classpath resources into the native binary unless a resource-inclusion hint
 * ({@code RuntimeHints.resources().registerPattern(...)}) names them. Missing this hint boots clean
 * under the JVM (the resource is really on the classpath) and fails only in a native image, with a
 * plain {@link java.io.FileNotFoundException} from {@code ClassPathResource} — no
 * Jackson/reflection error in sight, which is what makes the gap easy to miss by symmetry with
 * {@link AppsRuntimeHints}.
 *
 * <p>For every Spring bean whose class declares an {@code @McpUi}-annotated method with a non-blank
 * {@link McpUi#resource()}, resolves {@code ${...}} placeholders the same way the runtime
 * contributor does (via the owning {@link ConfigurableBeanFactory}'s embedded-value resolver) and
 * registers a resource pattern for classpath-scheme locations. {@code file:} (and other
 * non-classpath-scheme) locations are skipped deliberately: they are read from outside the image at
 * runtime and need no resource-inclusion hint. A placeholder that cannot be resolved at
 * AOT-processing time (e.g. it depends on a property not yet bound) falls back to registering the
 * literal, unresolved attribute value — see {@code docs/design/native-image.md} for that
 * limitation.
 */
public class AppsResourceAotProcessor implements BeanRegistrationAotProcessor {

  private static final Logger log = LoggerFactory.getLogger(AppsResourceAotProcessor.class);

  @Override
  public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
    List<String> locations =
        resourceLocations(registeredBean.getBeanClass(), registeredBean.getBeanFactory());
    if (locations.isEmpty()) {
      return null;
    }
    return (generationContext, beanRegistrationCode) -> {
      RuntimeHints hints = generationContext.getRuntimeHints();
      for (String location : locations) {
        registerPattern(hints, location);
      }
    };
  }

  private static List<String> resourceLocations(
      Class<?> beanClass, ConfigurableBeanFactory beanFactory) {
    List<String> locations = new ArrayList<>();
    for (Method method : beanClass.getMethods()) {
      McpUi ui = AnnotatedElementUtils.findMergedAnnotation(method, McpUi.class);
      if (ui == null || ui.resource().isBlank()) {
        continue;
      }
      locations.add(resolvePlaceholder(beanFactory, ui.resource()));
    }
    return locations;
  }

  private static String resolvePlaceholder(ConfigurableBeanFactory beanFactory, String raw) {
    try {
      String resolved = beanFactory.resolveEmbeddedValue(raw);
      return resolved == null ? raw : resolved;
    } catch (IllegalArgumentException ex) {
      log.warn(
          "MCP Apps: could not resolve placeholder \"{}\" on a @McpUi(resource = ...) bundle "
              + "location at AOT-processing time; registering the literal, unresolved value as a "
              + "native-image resource-inclusion hint instead. If the resolved value differs from "
              + "the literal, add a manual hint (see docs/design/native-image.md).",
          raw,
          ex);
      return raw;
    }
  }

  private static void registerPattern(RuntimeHints hints, String location) {
    String pattern = classpathPattern(location);
    if (pattern == null) {
      return;
    }
    hints.resources().registerPattern(pattern);
  }

  /**
   * Converts a Spring {@code ResourceLoader} location into a classpath resource-hint pattern, or
   * {@code null} if the location is not classpath-scheme (e.g. {@code file:...}), which needs no
   * inclusion hint since it is read from outside the native image at runtime.
   */
  private static String classpathPattern(String location) {
    String path;
    if (location.startsWith("classpath*:")) {
      path = location.substring("classpath*:".length());
    } else if (location.startsWith("classpath:")) {
      path = location.substring("classpath:".length());
    } else if (location.contains(":")) {
      return null;
    } else {
      path = location;
    }
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
