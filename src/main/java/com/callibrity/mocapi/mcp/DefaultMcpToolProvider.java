package com.callibrity.mocapi.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultMcpToolProvider implements McpToolProvider {

// ------------------------------ FIELDS ------------------------------

    private final List<McpTool> tools;

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface McpToolProvider ---------------------

    @Override
    public List<McpTool> getTools() {
        return List.copyOf(tools);
    }

}
