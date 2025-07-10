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
package com.callibrity.mocapi.tools.annotation;

import com.callibrity.mocapi.tools.McpTool;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import com.callibrity.ripcurl.core.exception.JsonRpcInternalErrorException;
import com.callibrity.ripcurl.core.invoke.JsonMethodInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.reflect.Method;
import java.util.List;

import static com.callibrity.mocapi.server.util.Names.humanReadableName;
import static com.callibrity.mocapi.server.util.Names.identifier;
import static java.util.Optional.ofNullable;


public class AnnotationMcpTool implements McpTool {

// ------------------------------ FIELDS ------------------------------

    private final String name;
    private final String title;
    private final String description;
    private final JsonMethodInvoker invoker;
    private final ObjectNode inputSchema;
    private final ObjectNode outputSchema;

// -------------------------- STATIC METHODS --------------------------

    public static List<AnnotationMcpTool> createTools(ObjectMapper mapper, MethodSchemaGenerator generator, Object targetObject) {
        return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), Tool.class).stream()
                .map(m -> new AnnotationMcpTool(mapper, generator, targetObject, m))
                .toList();
    }

// --------------------------- CONSTRUCTORS ---------------------------

    AnnotationMcpTool(ObjectMapper mapper, MethodSchemaGenerator generator, Object targetObject, Method method) {
        var annotation = method.getAnnotation(Tool.class);
        this.name = nameOf(targetObject, method, annotation);
        this.title = titleOf(targetObject, method, annotation);
        this.description = descriptionOf(targetObject, method, annotation);
        this.invoker = new JsonMethodInvoker(mapper, targetObject, method);
        this.inputSchema = generator.generateInputSchema(targetObject, method);
        this.outputSchema = generator.generateOutputSchema(targetObject, method);
        var outputSchemaType = outputSchema.get("type").asText();
        if (!"object".equals(outputSchemaType)) {
            throw new IllegalArgumentException(String.format("MCP tool \"%s\" returns JSON schema type \"%s\" (object is required).", name, outputSchemaType));
        }
    }

    private static String nameOf(Object targetObject, Method method, Tool annotation) {
        return ofNullable(StringUtils.trimToNull(annotation.name()))
                .orElseGet(() -> identifier(targetObject, method));
    }

    private static String titleOf(Object targetObject, Method method, Tool annotation) {
        return ofNullable(StringUtils.trimToNull(annotation.title()))
                .orElseGet(() -> humanReadableName(targetObject, method));
    }

    private static String descriptionOf(Object targetObject, Method method, Tool annotation) {
        return ofNullable(StringUtils.trimToNull(annotation.description()))
                .orElseGet(() -> humanReadableName(targetObject, method));
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface McpTool ---------------------

    @Override
    public String name() {
        return name;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ObjectNode inputSchema() {
        return inputSchema;
    }

    @Override
    public ObjectNode outputSchema() {
        return outputSchema;
    }

    @Override
    public ObjectNode call(ObjectNode parameters) {
        var result = invoker.invoke(parameters);
        if (result.isObject()) {
            return (ObjectNode) result;
        }
        throw new JsonRpcInternalErrorException(String.format("McpTool %s returned non-object (%s) result.", name, result.getNodeType()));
    }

}
