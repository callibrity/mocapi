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
package com.callibrity.mocapi.examples.tools;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.api.tools.ToolService;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.StringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
@ToolService
public class HelloTool {

  public static final String FIRST_NAME_PROP = "firstName";
  public static final String LAST_NAME_PROP = "lastName";

  @ToolMethod(name = "hello", description = "Returns a greeting message")
  public HelloResponse sayHello(String name) {
    return new HelloResponse(String.format("Hello, %s!", name));
  }

  @ToolMethod(
      name = "hello-elicitation",
      description = "Returns a greeting message after elicitation")
  public HelloResponse sayHelloElicitation(McpToolContext ctx) {
    var properties =
        new LinkedHashMap<String, com.callibrity.mocapi.model.PrimitiveSchemaDefinition>();
    properties.put(FIRST_NAME_PROP, new StringSchema("First Name", null, null, null, null, null));
    properties.put(LAST_NAME_PROP, new StringSchema("Last Name", null, null, null, null, null));
    var schema = new RequestedSchema(properties, List.of(FIRST_NAME_PROP, LAST_NAME_PROP));
    var params =
        new ElicitRequestFormParams("form", "Please tell me about yourself!", schema, null, null);
    var result = ctx.elicit(params);
    var firstName = result.getString(FIRST_NAME_PROP);
    var lastName = result.getString(LAST_NAME_PROP);
    return new HelloResponse(String.format("Hello, %s %s!", firstName, lastName));
  }

  public record HelloResponse(String message) {}
}
