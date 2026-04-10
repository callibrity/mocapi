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

import com.callibrity.mocapi.model.EmptyResult;
import com.callibrity.mocapi.model.ListResourceTemplatesResult;
import com.callibrity.mocapi.model.ListResourcesResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import lombok.RequiredArgsConstructor;

@JsonRpcService
@RequiredArgsConstructor
public class McpResourceMethods {

  private final ResourcesRegistry resourcesRegistry;

  @JsonRpcMethod("resources/list")
  public ListResourcesResult listResources(@JsonRpcParams PaginatedRequestParams params) {
    return resourcesRegistry.listResources(params);
  }

  @JsonRpcMethod("resources/templates/list")
  public ListResourceTemplatesResult listResourceTemplates(
      @JsonRpcParams PaginatedRequestParams params) {
    return resourcesRegistry.listResourceTemplates(params);
  }

  @JsonRpcMethod("resources/read")
  public ReadResourceResult readResource(@JsonRpcParams ResourceRequestParams params) {
    return resourcesRegistry.readResource(params.uri());
  }

  @JsonRpcMethod("resources/subscribe")
  public EmptyResult subscribe(@JsonRpcParams ResourceRequestParams params) {
    return EmptyResult.INSTANCE;
  }

  @JsonRpcMethod("resources/unsubscribe")
  public EmptyResult unsubscribe(@JsonRpcParams ResourceRequestParams params) {
    return EmptyResult.INSTANCE;
  }
}
