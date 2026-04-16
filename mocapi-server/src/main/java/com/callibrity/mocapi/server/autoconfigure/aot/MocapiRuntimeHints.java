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
package com.callibrity.mocapi.server.autoconfigure.aot;

import com.callibrity.mocapi.server.session.McpSession;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Registers Jackson binding hints for every type in the {@code mocapi-model} package plus {@link
 * McpSession}, so Mocapi's wire envelopes (tool/prompt/resource results, content blocks, sealed
 * hierarchies, etc.) and the session record survive a GraalVM native-image build.
 *
 * <p>Per-user return types are covered separately by {@link MocapiServicesAotProcessor}; Ripcurl's
 * {@code RipCurlRuntimeHints} covers the {@code JsonRpcMessage} hierarchy.
 *
 * <p>The model package is scanned at AOT build time rather than enumerated, so new model types get
 * hints automatically without touching this class.
 */
public class MocapiRuntimeHints implements RuntimeHintsRegistrar {

  private static final String MODEL_PACKAGE = "com.callibrity.mocapi.model";
  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(hints.reflection(), McpSession.class);
    scanner()
        .findCandidateComponents(MODEL_PACKAGE)
        .forEach(
            bd ->
                BINDING.registerReflectionHints(
                    hints.reflection(),
                    ClassUtils.resolveClassName(bd.getBeanClassName(), classLoader)));
    registerJsonSkemaMetaSchemaResources(hints);
  }

  /**
   * json-sKema (used for tool/input schema validation) ships its JSON Schema draft meta-schemas as
   * classpath resources under {@code json-meta-schemas/**} but does not yet ship native-image
   * reachability metadata. Register the resource patterns here so those files survive a {@code
   * native-image} build and the library can load them at runtime. Belt-and-suspenders once
   * json-sKema ships its own {@code META-INF/native-image/reachability-metadata.json}.
   */
  private static void registerJsonSkemaMetaSchemaResources(RuntimeHints hints) {
    hints.resources().registerPattern("json-meta-schemas/*");
    hints.resources().registerPattern("json-meta-schemas/draft2020-12/*");
  }

  /**
   * {@link ClassPathScanningCandidateComponentProvider} defaults to excluding interfaces, abstract
   * classes, and types without a {@code @Component}-family annotation. Override both so it surfaces
   * every class in the package — sealed interfaces, records, enums, the lot.
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
