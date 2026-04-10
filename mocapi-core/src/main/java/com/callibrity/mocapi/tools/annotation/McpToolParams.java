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
package com.callibrity.mocapi.tools.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool method parameter to receive the entire {@code tools/call} arguments object
 * deserialized into a typed record.
 *
 * <p>This is the tool-layer parallel to RipCurl's {@code @JsonRpcParams} annotation. It lets tool
 * authors define a record for their tool's parameters and have the framework bind the whole
 * argument object to it in one step:
 *
 * <pre>{@code
 * public record GreetRequest(String name, int volume) {}
 *
 * @ToolMethod(name = "greet", description = "Greets the user")
 * public String greet(@McpToolParams GreetRequest request) {
 *   return "Hello, " + request.name().repeat(request.volume());
 * }
 * }</pre>
 *
 * <p>The framework's input schema generation derives the tool's JSON schema from the record's
 * components. The client still sends the arguments as a flat JSON object matching the record's
 * shape ({@code {"name": "...", "volume": ...}}) — the annotation only affects how the framework
 * binds the parsed JSON to the Java method.
 *
 * <p>At most one parameter per tool method may be annotated with {@code @McpToolParams}. The
 * parameter's type must be a Jackson-deserializable type (typically a record). The tool method may
 * additionally declare an {@link com.callibrity.mocapi.stream.McpStreamContext} parameter for
 * streaming.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolParams {}
