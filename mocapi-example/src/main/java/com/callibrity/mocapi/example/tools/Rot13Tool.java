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
package com.callibrity.mocapi.example.tools;


import com.callibrity.mocapi.tools.annotation.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;

@Component
public class Rot13Tool {

    @Tool(description = "A ROT-13 encoding utility.")
    public Rot13Response encode(@Schema(description = "The text to be ROT-13 encoded.") String text) {
        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            if ('a' <= c && c <= 'z') {
                result.append((char) ((c - 'a' + 13) % 26 + 'a'));
            } else if ('A' <= c && c <= 'Z') {
                result.append((char) ((c - 'A' + 13) % 26 + 'A'));
            } else {
                result.append(c); // leave non-alphabetic characters unchanged
            }
        }

        return new Rot13Response(result.toString());
    }

    public record Rot13Response(@Schema(description = "The ROT-13 encoded text.") String encoded) {
    }
}
