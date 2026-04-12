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

import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.StringSchema;
import com.callibrity.mocapi.server.tools.McpToolContext;
import com.callibrity.mocapi.server.tools.annotation.ToolMethod;
import com.callibrity.mocapi.server.tools.annotation.ToolService;
import java.util.LinkedHashMap;
import java.util.List;

@ToolService
public class HelloTool {

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
    properties.put("firstName", new StringSchema("First Name", null, null, null, null, null));
    properties.put("lastName", new StringSchema("Last Name", null, null, null, null, null));
    var schema = new RequestedSchema(properties, List.of("firstName", "lastName"));
    var params =
        new ElicitRequestFormParams("form", "Please tell me about yourself!", schema, null, null);
    var result = ctx.elicit(params);
    var firstName = result.getString("firstName");
    var lastName = result.getString("lastName");
    return new HelloResponse(String.format("Hello, %s %s!", firstName, lastName));
  }

  public record HelloResponse(String message) {}
}
