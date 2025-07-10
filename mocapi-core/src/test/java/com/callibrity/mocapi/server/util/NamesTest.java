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
package com.callibrity.mocapi.server.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamesTest {

    @Test
    void humanReadableShouldBeCapitalizedWords() throws Exception{
        var hello = new Hello();
        var name = Names.humanReadableName(hello, Hello.class.getMethod("sayHello", String.class));
        assertThat(name).isEqualTo("Hello - Say Hello");
    }

    @Test
    void identifierShouldBeKebabCaseDotSeparated() throws Exception{
        var hello = new Hello();
        var name = Names.identifier(hello, Hello.class.getMethod("sayHello", String.class));
        assertThat(name).isEqualTo("hello.say-hello");
    }
}