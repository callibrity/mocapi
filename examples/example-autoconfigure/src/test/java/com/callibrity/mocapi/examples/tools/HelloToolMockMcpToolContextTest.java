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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.test.MockMcpToolContext;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Demonstrates unit-testing a real {@code @ToolMethod} handler against {@link MockMcpToolContext} —
 * no Spring context, no transport, just the tool instance and the mock.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HelloToolMockMcpToolContextTest {

  @Test
  void elicitation_based_greeting_uses_mocked_client_response() {
    ObjectNode content = JsonMapper.builder().build().createObjectNode();
    content.put("firstName", "Jane");
    content.put("lastName", "Doe");

    var ctx =
        new MockMcpToolContext().elicitResponse(new ElicitResult(ElicitAction.ACCEPT, content));

    var response = new HelloTool().sayHelloElicitation(ctx);

    assertThat(response.message()).isEqualTo("Hello, Jane Doe!");
    assertThat(ctx.elicitCalls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.params().message()).isEqualTo("Please tell me about yourself!");
              assertThat(call.params().requestedSchema().properties())
                  .containsKeys("firstName", "lastName");
            });
  }
}
