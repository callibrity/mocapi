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
package com.callibrity.mocapi.resources.annotation;

import com.callibrity.mocapi.resources.McpResource;
import com.callibrity.mocapi.resources.ReadResourceResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.callibrity.mocapi.server.util.Names.humanReadableName;
import static com.callibrity.mocapi.server.util.Names.identifier;
import static java.util.Optional.ofNullable;

@RequiredArgsConstructor
public class AnnotationMcpResource implements McpResource {

    private final Object targetObject;
    private final Method method;
    private final Resource annotation;

    public static List<AnnotationMcpResource> createResources(Object targetObject) {
        return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), Resource.class).stream()
                .peek(AnnotationMcpResource::verifyMethodSignature)
                .map(method -> new AnnotationMcpResource(targetObject, method, method.getAnnotation(Resource.class)))
                .toList();
    }

    private static void verifyMethodSignature(Method method) {
        if (!ReadResourceResult.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException(String.format("Resource method '%s' returns %s (ReadResourceResult is required).", method.getName(), ClassUtils.getSimpleName(method.getReturnType())));
        }
    }

    @Override
    public String uri() {
        return ofNullable(StringUtils.trimToNull(annotation.uri()))
                .orElseGet(() -> identifier(targetObject, method));
    }

    @Override
    public String name() {
        return ofNullable(StringUtils.trimToNull(annotation.name()))
                .orElseGet(() -> humanReadableName(targetObject, method));
    }

    @Override
    public String title() {
        return ofNullable(StringUtils.trimToNull(annotation.title()))
                .orElseGet(() -> humanReadableName(targetObject, method));
    }

    @Override
    public String description() {
        return ofNullable(StringUtils.trimToNull(annotation.description()))
                .orElseGet(() -> humanReadableName(targetObject, method));
    }

    @Override
    public String mimeType() {
        return annotation.mimeType();
    }

    @Override
    public ReadResourceResult read(Map<String, String> parameters) {
        try {
            return (ReadResourceResult) method.invoke(targetObject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Error invoking resource method", e);
        }
    }
}
