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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HelloTool {

  @McpTool(name = "hello", description = "Returns a greeting message")
  public HelloResponse sayHello(String name) {
    // log.info("Saying hello to " + name);
    return new HelloResponse(String.format("Hello, %s!", name));
  }

  @McpTool(name = "hello-elicitation", description = "Returns a greeting message after elicitation")
  public HelloResponse sayHelloElicitation(McpToolContext ctx) {
    var result =
        ctx.elicit(
            "Please tell me about yourself!",
            schema -> schema.string("firstName", "First Name").string("lastName", "Last Name"));
    var firstName = result.getString("firstName");
    var lastName = result.getString("lastName");
    return new HelloResponse(String.format("Hello, %s %s!", firstName, lastName));
  }

  public record HelloResponse(String message) {}
}
