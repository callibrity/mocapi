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
package com.callibrity.mocapi.api.prompts.template;

import com.callibrity.mocapi.model.GetPromptResult;
import java.util.Map;

/**
 * A compiled prompt template, bound to a specific message {@link com.callibrity.mocapi.model.Role
 * role}, that can be rendered many times against different argument maps. Obtain instances from a
 * {@link PromptTemplateFactory}.
 */
public interface PromptTemplate {

  /**
   * Renders this template into a {@link GetPromptResult} containing a single {@link
   * com.callibrity.mocapi.model.PromptMessage} with the template's role and the rendered text.
   *
   * @param args the named values to substitute into the template
   * @return a {@code GetPromptResult} with one message
   */
  GetPromptResult render(Map<String, String> args);
}
