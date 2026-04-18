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
package com.callibrity.mocapi.jakarta.fixture;

import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Resource fixture whose URI-template variable carries a runtime jakarta validation pattern. Serves
 * the integration tests that verify a {@code resources/read} call with a URI matching the template
 * but producing a pattern-violating variable surfaces as a JSON-RPC {@code -32602 Invalid params}
 * error with the per-violation detail populated in the response's {@code data} field.
 */
@ResourceService
public class ValidatedResources {

  @ResourceTemplateMethod(
      uriTemplate = "val://{code}",
      name = "Validated",
      description = "Returns a stub config; template variable must be all-uppercase letters",
      mimeType = "text/plain")
  public ReadResourceResult readCoded(@Pattern(regexp = "^[A-Z]+$") String code) {
    return new ReadResourceResult(
        List.of(new TextResourceContents("val://" + code, "text/plain", "code=" + code)));
  }
}
