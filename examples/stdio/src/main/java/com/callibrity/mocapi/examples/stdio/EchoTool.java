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
package com.callibrity.mocapi.examples.stdio;

import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.api.tools.ToolService;
import org.springframework.stereotype.Component;

@Component
@ToolService
public class EchoTool {

  @ToolMethod(name = "echo", description = "Echoes the input string back to the caller")
  public EchoResponse echo(String message) {
    return new EchoResponse(message);
  }

  public record EchoResponse(String echoed) {}
}
