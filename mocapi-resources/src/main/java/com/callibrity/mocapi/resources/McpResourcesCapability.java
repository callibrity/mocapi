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
package com.callibrity.mocapi.resources;

import com.callibrity.mocapi.server.McpServerCapability;
import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.callibrity.ripcurl.core.util.LazyInitializer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@JsonRpcService
public class McpResourcesCapability implements McpServerCapability {

    private final LazyInitializer<Map<String, McpResource>> resources;

    public McpResourcesCapability(List<McpResourceProvider> resourceProviders) {
        this.resources = LazyInitializer.of(() -> resourceProviders.stream()
                .flatMap(provider -> provider.getMcpResources().stream())
                .collect(Collectors.toMap(McpResource::uri, r -> r)));
    }

    @Override
    public String name() {
        return "resources";
    }

    @Override
    public ResourcesCapabilityDescriptor describe() {
        return new ResourcesCapabilityDescriptor(false);
    }

    @JsonRpc("resources/list")
    public ListResourcesResponse listResources(String cursor) {
        var descriptors = resources.get().values().stream()
                .map(r -> new McpResourceDescriptor(r.uri(), r.name(), r.title(), r.description(), r.mimeType()))
                .sorted(Comparator.comparing(McpResourceDescriptor::uri))
                .toList();
        return new ListResourcesResponse(descriptors, null);
    }

    @JsonRpc("resources/read")
    public ReadResourceResult readResource(String uri) {
        var resource = lookupResource(uri);
        return resource.read(Map.of());
    }

    private McpResource lookupResource(String uri) {
        return ofNullable(resources.get().get(uri))
                .orElseThrow(() -> new JsonRpcInvalidParamsException(String.format("Resource %s not found.", uri)));
    }

    public record ResourcesCapabilityDescriptor(boolean listChanged) {
    }

    public record ListResourcesResponse(List<McpResourceDescriptor> resources, String nextCursor) {
    }

    public record McpResourceDescriptor(String uri, String name, String title, String description, String mimeType) {
    }
}
