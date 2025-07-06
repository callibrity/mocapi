package com.callibrity.mocapi.mcp.method;

import com.callibrity.mocapi.mcp.McpTool;
import com.callibrity.mocapi.mcp.McpToolProvider;
import com.callibrity.mocapi.schema.MethodSchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.util.List;

public class MethodMcpToolProvider implements McpToolProvider {

// ------------------------------ FIELDS ------------------------------

    private final List<MethodMcpTool> tools;

// --------------------------- CONSTRUCTORS ---------------------------

    public MethodMcpToolProvider(ObjectMapper mapper, MethodSchemaGenerator generator, Object targetObject) {
        this.tools = MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), Tool.class).stream()
                .map(m -> new MethodMcpTool(mapper, generator, targetObject, m))
                .toList();
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface McpToolProvider ---------------------

    @Override
    public List<McpTool> getTools() {
        return List.copyOf(tools);
    }

}
