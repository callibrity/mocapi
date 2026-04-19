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
package com.callibrity.mocapi.server.observability;

/** SLF4J MDC keys (and handler-kind values) mocapi sets during handler dispatch. */
public final class McpMdcKeys {

  public static final String SESSION = "mcp.session";
  public static final String REQUEST = "mcp.request";
  public static final String HANDLER_KIND = "mcp.handler.kind";
  public static final String HANDLER_NAME = "mcp.handler.name";

  public static final String KIND_TOOL = "tool";
  public static final String KIND_PROMPT = "prompt";
  public static final String KIND_RESOURCE = "resource";
  public static final String KIND_RESOURCE_TEMPLATE = "resource-template";

  private McpMdcKeys() {}
}
