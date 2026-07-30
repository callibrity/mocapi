/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Parameters of a URL-mode elicitation, embedded in an {@link ElicitRequest}. The required {@code
 * mode: "url"} discriminator is contributed by {@link ElicitRequestParams}'s type info; there is no
 * {@code _meta} — this is an embedded object, not JSON-RPC request params. URL-mode elicitation is
 * declared not implemented by mocapi (ADR-0022).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElicitRequestURLParams(String message, String url) implements ElicitRequestParams {}
