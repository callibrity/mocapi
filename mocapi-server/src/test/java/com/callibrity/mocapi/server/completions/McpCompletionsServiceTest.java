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
package com.callibrity.mocapi.server.completions;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CompleteRequestParams;
import com.callibrity.mocapi.model.CompletionArgument;
import com.callibrity.mocapi.model.PromptReference;
import org.junit.jupiter.api.Test;

class McpCompletionsServiceTest {

  private final McpCompletionsService service = new McpCompletionsService();

  @Test
  void completeReturnsEmptyCompletion() {
    var params =
        new CompleteRequestParams(
            new PromptReference("ref/prompt", "my-prompt"),
            new CompletionArgument("arg1", "val"),
            null,
            null);

    var result = service.complete(params);

    assertThat(result.completion()).isNotNull();
    assertThat(result.completion().values()).isEmpty();
    assertThat(result.completion().total()).isZero();
    assertThat(result.completion().hasMore()).isFalse();
  }
}
