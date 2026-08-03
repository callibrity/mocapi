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
package com.callibrity.mocapi.o11y;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code MocapiO11yAutoConfiguration#networkTransport()}'s three-way branch (HTTP present
 * -&gt; {@code tcp}, HTTP absent + stdio present -&gt; {@code pipe}, neither present -&gt; {@code
 * null}). This module always has both {@code mocapi-streamable-http-transport} and {@code
 * mocapi-stdio-transport} as (optional) compile dependencies (see sibling {@code
 * StreamableHttpAutoConfigurationTest} / {@code StdioAutoConfigurationTest}), so both transport
 * marker classes are unconditionally present on the ordinary test classpath and only the "HTTP
 * wins" branch is reachable through normal Spring context tests (see {@link
 * MocapiO11yAutoConfigurationTest}). The private helper resolves classpath presence via {@code
 * MocapiO11yAutoConfiguration.class.getClassLoader()} — a plain classloader lookup, not a
 * Spring-context condition — so Spring Boot's {@code FilteredClassLoader} test support (which only
 * affects condition evaluation inside an {@code ApplicationContextRunner}) cannot hide the classes
 * for this method. Instead, this test loads a private, isolated copy of the autoconfiguration class
 * through a classloader that reports selected transport classes as absent, then invokes the private
 * method via reflection.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiO11yAutoConfigurationNetworkTransportTest {

  private static final String AUTOCONFIG_CLASS =
      "com.callibrity.mocapi.o11y.MocapiO11yAutoConfiguration";
  private static final String HTTP_TRANSPORT_CLASS =
      "com.callibrity.mocapi.transport.http.StreamableHttpTransport";
  private static final String STDIO_TRANSPORT_CLASS =
      "com.callibrity.mocapi.transport.stdio.StdioServer";

  @Test
  void resolves_to_pipe_when_only_the_stdio_transport_marker_class_is_visible()
      throws ReflectiveOperationException {
    assertThat(networkTransport(HTTP_TRANSPORT_CLASS)).isEqualTo("pipe");
  }

  @Test
  void resolves_to_null_when_neither_transport_marker_class_is_visible()
      throws ReflectiveOperationException {
    assertThat(networkTransport(HTTP_TRANSPORT_CLASS, STDIO_TRANSPORT_CLASS)).isNull();
  }

  @Test
  void resolves_to_tcp_when_the_http_transport_marker_class_is_visible()
      throws ReflectiveOperationException {
    // Pins the "HTTP wins when both are present" precedence documented on the autoconfiguration
    // class, using the same isolated-loader mechanism as the other two branches (rather than
    // relying on this module's ordinary classpath, which always has both present).
    assertThat(networkTransport()).isEqualTo("tcp");
  }

  private static String networkTransport(String... hiddenClassNames)
      throws ReflectiveOperationException {
    var loader =
        new SingleClassIsolatingClassLoader(
            MocapiO11yAutoConfigurationNetworkTransportTest.class.getClassLoader(),
            AUTOCONFIG_CLASS,
            Set.of(hiddenClassNames));
    Class<?> isolated = Class.forName(AUTOCONFIG_CLASS, true, loader);
    Method method = isolated.getDeclaredMethod("networkTransport");
    method.setAccessible(true);
    try {
      return (String) method.invoke(null);
    } catch (InvocationTargetException e) {
      throw new AssertionError("networkTransport() threw", e.getCause());
    }
  }

  /**
   * Delegates every class load to the parent loader except {@code isolatedClassName} (defined fresh
   * under this loader, so its own {@code getClassLoader()} returns this instance) and any name in
   * {@code hiddenClassNames} (reported not found, simulating classpath absence).
   */
  private static final class SingleClassIsolatingClassLoader extends ClassLoader {

    private final String isolatedClassName;
    private final Set<String> hiddenClassNames;

    SingleClassIsolatingClassLoader(
        ClassLoader parent, String isolatedClassName, Set<String> hiddenClassNames) {
      super(parent);
      this.isolatedClassName = isolatedClassName;
      this.hiddenClassNames = hiddenClassNames;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (hiddenClassNames.contains(name)) {
        throw new ClassNotFoundException(name);
      }
      if (!name.equals(isolatedClassName)) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          loaded = defineIsolatedClass(name);
        }
        if (resolve) {
          resolveClass(loaded);
        }
        return loaded;
      }
    }

    private Class<?> defineIsolatedClass(String name) throws ClassNotFoundException {
      String resourcePath = name.replace('.', '/') + ".class";
      try (InputStream in = getParent().getResourceAsStream(resourcePath)) {
        if (in == null) {
          throw new ClassNotFoundException(name);
        }
        byte[] bytecode = in.readAllBytes();
        // Reuse the parent loader's ProtectionDomain (real target/classes CodeSource) rather than
        // the no-location default: JaCoco's runtime agent excludes classes with no CodeSource
        // location from coverage by default, which would otherwise silently zero out this test's
        // contribution to MocapiO11yAutoConfiguration's coverage.
        ProtectionDomain domain =
            MocapiO11yAutoConfigurationNetworkTransportTest.class.getProtectionDomain();
        return defineClass(name, bytecode, 0, bytecode.length, domain);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read class bytes for " + name, e);
      }
    }
  }
}
