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
package com.callibrity.mocapi.server.tools;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;

/**
 * Validates a tool's incoming {@link JsonNode} arguments against the tool's compiled input schema
 * before the reflective call. Added as the innermost interceptor per {@code CallToolHandler}, so it
 * runs after any ambient observability interceptors and fails fast with {@code -32602} on a schema
 * mismatch.
 */
final class InputSchemaValidatingInterceptor implements MethodInterceptor<JsonNode> {

  private final Schema inputSchema;

  InputSchemaValidatingInterceptor(Schema inputSchema) {
    this.inputSchema = inputSchema;
  }

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    JsonNode args = invocation.argument();
    // json-sKema 0.29's Validator is NOT thread-safe — its internal SchemaVisitor mutates a
    // shared ArrayList that races under concurrent invocation. A fresh Validator per call is
    // the safe contract until json-sKema ships a thread-safe implementation. Do NOT cache
    // Validator.forSchema(...) at construction time.
    ValidationFailure failure =
        Validator.forSchema(inputSchema).validate(new JsonParser(args.toString()).parse());
    if (failure != null) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, failure.getMessage());
    }
    return invocation.proceed();
  }

  @Override
  public String toString() {
    return "Validates tool arguments against the tool's input JSON schema";
  }
}
