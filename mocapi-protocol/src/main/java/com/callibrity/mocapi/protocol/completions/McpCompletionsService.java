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
package com.callibrity.mocapi.protocol.completions;

import com.callibrity.mocapi.model.CompleteRequestParams;
import com.callibrity.mocapi.model.CompleteResult;
import com.callibrity.mocapi.model.Completion;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import java.util.List;

/** Handles completion/complete requests. Returns an empty completion by default. */
@JsonRpcService
public class McpCompletionsService {

  private static final CompleteResult EMPTY =
      new CompleteResult(new Completion(List.of(), 0, false));

  @JsonRpcMethod("completion/complete")
  public CompleteResult complete(@JsonRpcParams CompleteRequestParams params) {
    return EMPTY;
  }
}
