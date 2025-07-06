package com.callibrity.mocapi.mcp.method;

import com.callibrity.mocapi.jsonrpc.method.invoke.JsonInvocationException;
import com.callibrity.mocapi.jsonrpc.method.invoke.JsonMethodInvoker;
import com.callibrity.mocapi.mcp.McpTool;
import com.callibrity.mocapi.schema.MethodSchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

public class MethodMcpTool implements McpTool {

// ------------------------------ FIELDS ------------------------------

    private final String name;
    private final String description;
    private final String title;
    private final JsonMethodInvoker invoker;
    private final ObjectNode inputSchema;
    private final ObjectNode outputSchema;

// --------------------------- CONSTRUCTORS ---------------------------

    public MethodMcpTool(ObjectMapper mapper, MethodSchemaGenerator generator, Object targetObject, Method method) {
        var annotation = method.getAnnotation(Tool.class);
        this.name = defaultIfEmpty(annotation::name, method::getName);
        this.description = defaultIfEmpty(annotation::description, method::getName);
        this.title = defaultIfEmpty(annotation::title, method::getName);
        this.invoker = new JsonMethodInvoker(mapper, targetObject, method);
        this.inputSchema = generator.generateInputSchema(targetObject, method);
        this.outputSchema = generator.generateOutputSchema(targetObject, method);
    }

    private String defaultIfEmpty(Supplier<String> original, Supplier<String> def) {
        return ofNullable(original.get()).map(StringUtils::stripToNull).orElseGet(def);
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
    public boolean isEnabledFor(String userId) {
        return true;
    }

    @Override
    public ObjectNode call(ObjectNode parameters) {
        var result = invoker.invoke(parameters);
        if (result.isObject()) {
            return (ObjectNode) result;
        }
        throw new JsonInvocationException("McpTool %s returned non-object (%s) result.", name, result.getNodeType());
    }

}
