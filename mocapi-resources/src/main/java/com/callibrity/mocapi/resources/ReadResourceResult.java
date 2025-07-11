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
package com.callibrity.mocapi.resources;

import com.callibrity.mocapi.resources.content.BlobResourceContents;
import com.callibrity.mocapi.resources.content.ResourceContents;
import com.callibrity.mocapi.resources.content.TextResourceContents;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadResourceResult(List<ResourceContents> contents) {

    public static ReadResourceResult text(String text, String mimeType, String uri) {
        return new ReadResourceResult(List.of(new TextResourceContents(uri, text, mimeType)));
    }

    public static ReadResourceResult text(String text, String uri) {
        return new ReadResourceResult(List.of(new TextResourceContents(uri, text, "text/plain")));
    }

    public static ReadResourceResult blob(String blob, String mimeType, String uri) {
        return new ReadResourceResult(List.of(new BlobResourceContents(uri, blob, mimeType)));
    }


}
