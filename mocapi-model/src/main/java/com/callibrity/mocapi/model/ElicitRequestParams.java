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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The parameters of an embedded {@link ElicitRequest}, discriminated by {@code mode}. The spec
 * makes {@code mode} optional on the form variant (form is the implicit default), hence {@code
 * defaultImpl}; the URL variant requires {@code mode: "url"}.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "mode",
    defaultImpl = ElicitRequestFormParams.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ElicitRequestFormParams.class, name = "form"),
  @JsonSubTypes.Type(value = ElicitRequestURLParams.class, name = "url")
})
public sealed interface ElicitRequestParams
    permits ElicitRequestFormParams, ElicitRequestURLParams {}
