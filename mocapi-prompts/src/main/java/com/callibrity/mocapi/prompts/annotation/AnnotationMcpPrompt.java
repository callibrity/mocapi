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
package com.callibrity.mocapi.prompts.annotation;

import com.callibrity.mocapi.prompts.GetPromptResult;
import com.callibrity.mocapi.prompts.McpPrompt;
import com.callibrity.mocapi.prompts.PromptArgument;
import com.callibrity.mocapi.server.util.Parameters;
import com.callibrity.ripcurl.core.exception.JsonRpcInternalErrorException;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.callibrity.mocapi.server.util.Names.humanReadableName;
import static com.callibrity.mocapi.server.util.Names.identifier;
import static java.util.Optional.ofNullable;

public class AnnotationMcpPrompt implements McpPrompt {

// ------------------------------ FIELDS ------------------------------

    private final Object targetObject;
    private final Method method;
    private final String name;
    private final String description;
    private final List<PromptArgument> promptArguments;

// -------------------------- STATIC METHODS --------------------------

    public static List<AnnotationMcpPrompt> createPrompts(Object targetObject) {
        return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), Prompt.class).stream()
                .map(m -> new AnnotationMcpPrompt(targetObject, m))
                .toList();
    }

// --------------------------- CONSTRUCTORS ---------------------------

    AnnotationMcpPrompt(Object targetObject, Method method) {
        verifyMethodSignature(method);
        this.targetObject = targetObject;
        this.method = method;
        var annotation = method.getAnnotation(Prompt.class);
        this.name = nameOf(targetObject, method, annotation);
        this.description = descriptionOf(targetObject, method, annotation);
        this.promptArguments = Arrays.stream(method.getParameters())
                .map(p -> new PromptArgument(p.getName(), Parameters.descriptionOf(p), Parameters.isRequired(p)))
                .toList();
    }

    private static void verifyMethodSignature(Method method) {
        if(!GetPromptResult.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException(String.format("Prompt method '%s' returns %s (GetPromptResult is required).", method.getName(), ClassUtils.getSimpleName(method.getReturnType())));
        }
        if(!Arrays.stream(method.getParameterTypes()).allMatch(String.class::equals)) {
            throw new IllegalArgumentException(String.format("Prompt method '%s' has non-String parameters.", method.getName()));
        }
    }

    private static String nameOf(Object targetObject, Method method, Prompt annotation) {
        return ofNullable(StringUtils.trimToNull(annotation.name()))
                .orElseGet(() -> identifier(targetObject, method));
    }

    private static String descriptionOf(Object targetObject, Method method, Prompt annotation) {
        return ofNullable(StringUtils.trimToNull(annotation.description()))
                .orElseGet(() -> humanReadableName(targetObject, method));
    }

    private static Object extractArgumentValue(PromptArgument argument, Map<String,String> arguments) {
        var value = arguments.get(argument.name());
        if (value == null && argument.required()) {
            throw new JsonRpcInvalidParamsException(String.format("Required argument '%s' is missing.", argument.name()));
        }
        return value;
    }
// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface McpPrompt ---------------------

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<PromptArgument> arguments() {
        return List.copyOf(promptArguments);
    }

    @Override
    public GetPromptResult getPrompt(Map<String, String> arguments) {
        var params = promptArguments.stream()
                .map(p -> extractArgumentValue(p, arguments))
                .toArray();
        try {
            var result = method.invoke(targetObject, params);
            if (result instanceof GetPromptResult r) {
                return r;
            }
            throw new JsonRpcInternalErrorException(String.format("Prompt \"%s\" did not return a GetPromptResult.", name));
        } catch (ReflectiveOperationException e) {
            throw new JsonRpcInternalErrorException(String.format("Unable to get prompt \"%s\".", name), e);
        }
    }

}
