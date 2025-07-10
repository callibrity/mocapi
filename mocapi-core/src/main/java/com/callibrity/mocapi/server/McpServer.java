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
package com.callibrity.mocapi.server;


import com.callibrity.mocapi.client.ClientCapabilities;
import com.callibrity.mocapi.client.ClientInfo;
import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@JsonRpcService
public class McpServer {

// ------------------------------ FIELDS ------------------------------

    public static final String PROTOCOL_VERSION = "2025-06-18";

    private final ServerInfo serverInfo;
    private final String instructions;
    private final Map<String, Object> serverCapabilities;

// --------------------------- CONSTRUCTORS ---------------------------

    public McpServer(List<McpServerCapability> serverCapabilities, ServerInfo serverInfo, String instructions) {
        this.serverCapabilities = serverCapabilities.stream()
                .collect(Collectors.toMap(
                        McpServerCapability::name,
                        McpServerCapability::describe
                ));
        this.serverInfo = serverInfo;
        this.instructions = instructions;
    }

// -------------------------- OTHER METHODS --------------------------

    @JsonRpc("notifications/initialized")
    public void clientInitialized() {
        // Do nothing!
    }

    @JsonRpc("initialize")
    public InitializeResponse initialize(String protocolVersion,
                                         ClientCapabilities capabilities,
                                         ClientInfo clientInfo) {
        log.info("Client {} initializing with protocol version {} and capabilities {}.", clientInfo, protocolVersion, capabilities);
        return new InitializeResponse(PROTOCOL_VERSION,
                serverCapabilities,
                serverInfo,
                instructions);
    }

    public record InitializeResponse(
            String protocolVersion,
            Map<String,Object> capabilities,
            ServerInfo serverInfo,
            String instructions
    ) {
    }

    @JsonRpc("ping")
    public PingResponse ping() {
        return new PingResponse();
    }

    public record PingResponse() {
    }


}
