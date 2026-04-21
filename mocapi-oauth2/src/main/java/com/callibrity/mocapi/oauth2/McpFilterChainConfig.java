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
package com.callibrity.mocapi.oauth2;

import com.callibrity.mocapi.oauth2.token.McpTokenStrategy;
import java.util.List;

/**
 * Static inputs to {@link McpFilterChains#createMcpFilterChain}: the token-validation strategy, MCP
 * endpoint path, and user-supplied chain customizers. The {@code HttpSecurity} builder is passed
 * separately to the factory method since it is the mutable target being built against.
 *
 * @param tokenStrategy the bearer-token validation mode ({@link JwtMcpTokenStrategy} or {@link
 *     OpaqueTokenMcpTokenStrategy}); required
 * @param mcpEndpoint the path prefix of the MCP endpoint (default {@code /mcp}); used as the
 *     chain's {@code securityMatcher}
 * @param chainCustomizers user-supplied customizers invoked in order after mocapi's defaults
 */
public record McpFilterChainConfig(
    McpTokenStrategy tokenStrategy,
    String mcpEndpoint,
    List<McpFilterChainCustomizer> chainCustomizers) {}
