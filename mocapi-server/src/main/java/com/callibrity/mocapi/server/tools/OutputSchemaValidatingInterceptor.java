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

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in dev/test guardrail: validates a tool's return value against the tool's compiled output
 * schema after the reflective call. Enabled via {@code mocapi.tools.validate-output=true} (default
 * off) so production dispatch pays no validation cost, while {@code @SpringBootTest} runs can flip
 * it on to catch schema/reality drift before clients do.
 *
 * <p>Only installed by {@link CallToolHandlers} when the declared return type is a structured
 * POJO/record (i.e., the handler is paired with {@link StructuredResultMapper}). Handlers whose
 * declared return type is {@code void}/{@link Void} or {@link CallToolResult} do not advertise an
 * output schema and do not install this interceptor — so the only values this interceptor sees are
 * {@code null} (tool returned null from a non-void signature, tolerated) or a POJO/record whose
 * Jackson serialization is an object.
 */
final class OutputSchemaValidatingInterceptor implements MethodInterceptor<JsonNode> {

  private final Schema outputSchema;
  private final ObjectMapper objectMapper;

  OutputSchemaValidatingInterceptor(Schema outputSchema, ObjectMapper objectMapper) {
    this.outputSchema = outputSchema;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    Object result = invocation.proceed();
    if (result == null) {
      return null;
    }
    JsonNode json = objectMapper.valueToTree(result);
    // json-sKema 0.29's Validator is NOT thread-safe — its internal SchemaVisitor mutates a
    // shared ArrayList that races under concurrent invocation. A fresh Validator per call is
    // the safe contract until json-sKema ships a thread-safe implementation. Do NOT cache
    // Validator.forSchema(...) at construction time.
    ValidationFailure failure =
        Validator.forSchema(outputSchema).validate(new JsonParser(json.toString()).parse());
    if (failure != null) {
      throw new JsonRpcException(JsonRpcProtocol.INTERNAL_ERROR, failure.getMessage());
    }
    return result;
  }

  @Override
  public String toString() {
    return "Validates tool return value against the tool's output JSON schema";
  }
}
