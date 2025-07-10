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
package com.callibrity.mocapi.prompts.content;

import com.callibrity.mocapi.prompts.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class TextContentTest {

    @Test
    void shouldHaveTypeText() {
        var content = new TextContent("Hello, world!");
        assertThat(content.getType()).isEqualTo("text");
        assertThat(content.getText()).isEqualTo("Hello, world!");
    }

    @Test
    void shouldHaveAnnotations() {
        var annotations = new Annotations(List.of(Role.USER, Role.ASSISTANT), 0.5);
        var content = new TextContent("Hello, world!", annotations);
        assertThat(content.getType()).isEqualTo("text");
        assertThat(content.getText()).isEqualTo("Hello, world!");
        assertThat(content.getAnnotations()).isEqualTo(annotations);
    }

}