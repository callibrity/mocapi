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
package com.callibrity.mocapi.tasks.aot;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Registers Jackson binding hints for every type in the {@code mocapi-tasks} wire model package
 * ({@code com.callibrity.mocapi.tasks.model}) so the {@code io.modelcontextprotocol/tasks}
 * extension's envelopes ({@code CreateTaskResult}, {@code GetTaskResult}, {@code UpdateTaskResult},
 * {@code CancelTaskResult}, their params, and the {@code TaskStatus} enum) survive a GraalVM
 * native-image build.
 *
 * <p>{@code mocapi-server}'s {@code MocapiRuntimeHints} only scans {@code
 * com.callibrity.mocapi.model} — core has no reason to know about extension-owned packages, and
 * widening that scan would leak extension knowledge into core. Each extension that introduces its
 * own wire types is responsible for registering its own hints; this class is {@code mocapi-tasks}'
 * half of that contract, mirroring {@code MocapiRuntimeHints}' scan-the-model-package pattern
 * (including its {@link ClassPathScanningCandidateComponentProvider} configuration, copied locally
 * rather than shared, so core stays extension-agnostic).
 *
 * <p>The model package is scanned at AOT build time rather than enumerated, so new task wire types
 * get hints automatically without touching this class.
 */
public class TasksRuntimeHints implements RuntimeHintsRegistrar {

  private static final String MODEL_PACKAGE = "com.callibrity.mocapi.tasks.model";
  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    scanner()
        .findCandidateComponents(MODEL_PACKAGE)
        .forEach(
            bd ->
                BINDING.registerReflectionHints(
                    hints.reflection(),
                    ClassUtils.resolveClassName(bd.getBeanClassName(), classLoader)));
  }

  /**
   * {@link ClassPathScanningCandidateComponentProvider} defaults to excluding interfaces, abstract
   * classes, and types without a {@code @Component}-family annotation. Override both so it surfaces
   * every class in the package — records and enums alike.
   */
  private static ClassPathScanningCandidateComponentProvider scanner() {
    var scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return true;
          }
        };
    scanner.addIncludeFilter((reader, factory) -> true);
    return scanner;
  }
}
