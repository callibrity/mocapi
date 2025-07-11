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
package com.callibrity.mocapi.resources.util;

import com.callibrity.mocapi.resources.ReadResourceResult;
import com.callibrity.mocapi.resources.annotation.Resource;

public class HelloResource {

    @Resource(
            uri = "hello://greeting",
            name = "Hello Greeting",
            title = "Hello Greeting Resource",
            description = "A simple greeting resource",
            mimeType = "text/plain"
    )
    public ReadResourceResult getGreeting() {
        return ReadResourceResult.text("Hello from Mocapi Resources!");
    }
}
