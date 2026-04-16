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

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.session.McpSession;
import java.io.IOException;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;

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
    registerPackage(hints, classLoader, MODEL_PACKAGE, Tool.class);
  }

  /**
   * Walks every class (records, concrete classes, sealed-interface permits) in {@code packageName}
   * and registers Jackson binding hints for it. The {@code marker} type anchors the search to the
   * jar that actually contains the package so other jars declaring the same package are ignored.
   */
  private static void registerPackage(
      RuntimeHints hints, ClassLoader classLoader, String packageName, Class<?> marker) {
    String pattern = "classpath*:" + packageName.replace('.', '/') + "/**/*.class";
    var resolver = new PathMatchingResourcePatternResolver(classLoader);
    MetadataReaderFactory factory = new CachingMetadataReaderFactory(classLoader);
    try {
      for (Resource resource : resolver.getResources(pattern)) {
        var metadata = factory.getMetadataReader(resource).getClassMetadata();
        if (!metadata.getClassName().startsWith(packageName + ".")) {
          continue;
        }
        Class<?> type = Class.forName(metadata.getClassName(), false, classLoader);
        if (type.getPackage() != marker.getPackage()) {
          continue;
        }
        BINDING.registerReflectionHints(hints.reflection(), type);
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new IllegalStateException(
          "Failed to register Mocapi model hints for " + packageName, e);
    }
  }
}
