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

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

@UtilityClass
public class Names {

// -------------------------- STATIC METHODS --------------------------

    public static String humanReadableName(Object targetObject, Method method) {
        var className = ClassUtils.getShortClassName(targetObject.getClass());
        var methodName = method.getName();
        return String.format("%s - %s", capitalizedWords(className), capitalizedWords(methodName));
    }

    public static String capitalizedWords(String input) {
        return Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(input))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
    }

    public static String identifier(Object targetObject, Method method) {
        var className = ClassUtils.getShortClassName(targetObject.getClass());
        var methodName = method.getName();
        return String.format("%s.%s", kebab(className), kebab(methodName));
    }

    public static String kebab(String input) {
        return Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(input))
                .map(String::toLowerCase)
                .collect(Collectors.joining("-"));
    }

}
