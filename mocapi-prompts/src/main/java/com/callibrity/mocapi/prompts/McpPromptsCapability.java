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
package com.callibrity.mocapi.prompts;

import com.callibrity.mocapi.server.McpServerCapability;
import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.callibrity.ripcurl.core.util.LazyInitializer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@JsonRpcService
public class McpPromptsCapability implements McpServerCapability {

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface McpServerCapability ---------------------

    private final LazyInitializer<Map<String, McpPrompt>> prompts;

    public McpPromptsCapability(List<McpPromptProvider> providers) {
        this.prompts = new LazyInitializer<>(() -> providers.stream()
                .flatMap(provider -> provider.getMcpPrompts().stream())
                .collect(Collectors.toMap(McpPrompt::name, prompt -> prompt)));
    }

    @Override
    public String name() {
        return "prompts";
    }

    @Override
    public PromptsCapabilityDescriptor describe() {
        return new PromptsCapabilityDescriptor(false);
    }

    @JsonRpc("prompts/list")
    public ListPromptsResponse listPrompts(String cursor) {
        return new ListPromptsResponse(prompts.get().values().stream()
                .map(p -> new McpPromptDescriptor(p.name(), p.description(), p.arguments()))
                .toList(), null);
    }

    @JsonRpc("prompts/get")
    public GetPromptResult getPrompt(String name, Map<String, String> arguments) {
        return ofNullable(prompts.get().get(name))
                .map(p -> p.getPrompt(arguments))
                .orElseThrow(() -> new JsonRpcInvalidParamsException(String.format("Prompt '%s' not found.", name)));
    }
// -------------------------- INNER CLASSES --------------------------

    public record PromptsCapabilityDescriptor(boolean listChanged) {

    }

    public record ListPromptsResponse(List<McpPromptDescriptor> prompts, String nextCursor) {
    }

    public record McpPromptDescriptor(String name, String description, List<PromptArgument> arguments) {

    }

}
