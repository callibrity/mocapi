package com.callibrity.mocapi.web.mcp;

import com.callibrity.mocapi.jsonrpc.JsonRpcService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
@Validated
@RequiredArgsConstructor
@Slf4j
public class McpController {

    private final JsonRpcService jsonRpcService;

    @PostMapping
    public McpResponse mcp(@RequestBody @NotNull @Valid McpRequest request) {
        log.debug("JSON-RPC:{} params: {}", request.method(), request.params());
        var result = jsonRpcService.call(request.method(), request.params());
        log.debug("JSON-RPC:{} result:  {}", request.method(), result);
        return new McpResponse(request.jsonrpc(), result, null, request.id());
    }
}
