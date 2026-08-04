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
package com.callibrity.mocapi.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Guards every Spring-owned class name referenced from mocapi's {@code beforeName} / {@code
 * afterName} ordering attributes against Spring Boot relocating the class out from under us.
 *
 * <p>Name-based ordering references to absent classes are silent no-ops by design — which is
 * exactly right for optional third-party targets (Substrate, codec, ripcurl may legitimately be off
 * the classpath), and exactly wrong for Spring Boot's own auto-configurations, which are always
 * present but occasionally <em>move</em> between minor lines (Boot 4.1 relocated {@code
 * OAuth2ResourceServerAutoConfiguration} by dropping its {@code servlet} package segment). A moved
 * Boot target degrades ordering silently: nothing crashes, conditions simply evaluate in the wrong
 * order. This test turns that silence into a red build on whichever Boot line the suite runs
 * against — including a CI matrix leg running {@code -Dspring-boot.version=<next-minor>}.
 *
 * <p>Contract: a Boot-owned ordering reference may name multiple historical locations (the
 * dual-location pattern), and AT LEAST ONE must resolve on the running classpath. Non-Spring names
 * are ignored here — their absence is legitimate.
 */
class BootOrderingReferenceIntegrityTest {

  private static final String SPRING_PREFIX = "org.springframework.";
  private static final String MOCAPI_AUTOCONFIGURE_ROOT = "com.callibrity.mocapi";

  @Test
  void everySpringOwnedOrderingReferenceResolvesOnTheRunningBootLine() {
    List<String> unresolvedGroups = new ArrayList<>();
    int springReferenceGroups = 0;

    for (Class<?> autoConfig : mocapiAutoConfigurations()) {
      AutoConfiguration annotation =
          AnnotatedElementUtils.findMergedAnnotation(autoConfig, AutoConfiguration.class);
      if (annotation == null) {
        continue;
      }
      for (String[] group :
          List.of(springNames(annotation.beforeName()), springNames(annotation.afterName()))) {
        if (group.length == 0) {
          continue;
        }
        springReferenceGroups++;
        boolean anyResolves =
            Arrays.stream(group)
                .anyMatch(name -> ClassUtils.isPresent(name, getClass().getClassLoader()));
        if (!anyResolves) {
          unresolvedGroups.add(autoConfig.getSimpleName() + " -> " + Arrays.toString(group));
        }
      }
    }

    assertThat(springReferenceGroups)
        .as("the scan must find the known Spring-owned ordering references (sanity check)")
        .isGreaterThanOrEqualTo(2);
    assertThat(unresolvedGroups)
        .as(
            "every Spring-owned beforeName/afterName group must resolve on this Boot line; a "
                + "fully-unresolved group means Boot moved the class again — add its new FQCN to "
                + "the dual-location list")
        .isEmpty();
  }

  /**
   * All {@code @AutoConfiguration} classes in mocapi-autoconfigure, found by classpath scan so a
   * newly added auto-configuration is covered without editing this test.
   */
  private static List<Class<?>> mocapiAutoConfigurations() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(AutoConfiguration.class));
    List<Class<?>> classes = new ArrayList<>();
    for (BeanDefinition bd : scanner.findCandidateComponents(MOCAPI_AUTOCONFIGURE_ROOT)) {
      classes.add(
          ClassUtils.resolveClassName(
              bd.getBeanClassName(), BootOrderingReferenceIntegrityTest.class.getClassLoader()));
    }
    assertThat(classes).as("classpath scan must find mocapi's auto-configurations").isNotEmpty();
    return classes;
  }

  /**
   * Filters an ordering-attribute group down to Spring-owned names. Within one attribute, the
   * Spring names for a single logical target form the dual-location set; mocapi currently never
   * mixes two DIFFERENT Spring targets in one attribute, so group-level any-resolves is the right
   * granularity. (If that ever changes, split the attribute across two annotations or revisit this
   * test.)
   */
  private static String[] springNames(String[] names) {
    return Arrays.stream(names)
        .filter(name -> name.startsWith(SPRING_PREFIX))
        .toArray(String[]::new);
  }

  /** Compile-time proof this test's assumptions match Jackson-free plain-JUnit execution. */
  @Test
  void scannerFindsTheOauth2AndO11yReferenceSites() throws IOException {
    Map<String, Boolean> expectations =
        Map.of(
            "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
            true,
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration",
            ClassUtils.isPresent(
                "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration",
                BootOrderingReferenceIntegrityTest.class.getClassLoader()),
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
            ClassUtils.isPresent(
                "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
                BootOrderingReferenceIntegrityTest.class.getClassLoader()));
    // The two OAuth2 locations are mutually exclusive across Boot lines; exactly one resolves.
    long resolved =
        expectations.entrySet().stream()
            .filter(e -> !e.getKey().contains("Observation"))
            .filter(Map.Entry::getValue)
            .count();
    assertThat(resolved).as("exactly one OAuth2ResourceServerAutoConfiguration home").isEqualTo(1);
  }
}
