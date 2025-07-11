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
package com.callibrity.mocapi.resources.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ResourceContents {

    private final String uri;
    private final String mimeType;
    private final Map<String, Object> _meta;

    protected ResourceContents(String uri, String mimeType) {
        this(uri, mimeType, null);
    }

    protected ResourceContents(String uri, String mimeType, Map<String, Object> meta) {
        this.uri = uri;
        this.mimeType = mimeType;
        this._meta = meta;
    }

    public String getUri() {
        return uri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Map<String, Object> get_meta() {
        return _meta;
    }
}
