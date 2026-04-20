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
package com.callibrity.mocapi.server.util;

import java.util.Map;
import java.util.Optional;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;
import org.springframework.core.convert.ConversionService;

/**
 * Resolves method parameters from an incoming {@code Map<String, String>} of named string values.
 * If the parameter's declared type accepts a {@code Map<String, String>} (for example, the method
 * declares a {@code Map<String, String>} parameter to receive the whole map), the entire map is
 * supplied. Otherwise, the entry matching the parameter name is pulled from the map and converted
 * to the parameter's declared type via a Spring {@link ConversionService}.
 *
 * <p>Shared by annotation-driven prompt methods (MCP prompt arguments) and resource-template
 * methods (URI path variables).
 */
public class StringMapArgResolver implements ParameterResolver<Map<String, String>> {

  private static final TypeRef<Map<String, String>> MAP_TYPE = new TypeRef<>() {};

  private final ConversionService conversionService;

  public StringMapArgResolver(ConversionService conversionService) {
    this.conversionService = conversionService;
  }

  @Override
  public Optional<Binding<Map<String, String>>> bind(ParameterInfo info) {
    if (info.accepts(MAP_TYPE)) {
      return Optional.of(arguments -> arguments == null ? Map.<String, String>of() : arguments);
    }
    Class<?> targetType = info.resolvedType();
    if (!conversionService.canConvert(String.class, targetType)) {
      return Optional.empty();
    }
    String paramName = info.name();
    return Optional.of(
        arguments -> {
          var args = arguments == null ? Map.<String, String>of() : arguments;
          String raw = args.get(paramName);
          if (raw == null) {
            return null;
          }
          try {
            return conversionService.convert(raw, targetType);
          } catch (RuntimeException e) {
            throw new ParameterResolutionException(
                String.format(
                    "Unable to convert argument \"%s\" to %s: %s",
                    paramName, targetType.getName(), e.getMessage()),
                e);
          }
        });
  }
}
