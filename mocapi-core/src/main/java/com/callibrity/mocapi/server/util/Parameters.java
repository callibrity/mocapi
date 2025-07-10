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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Parameter;

import static java.util.Optional.ofNullable;

@UtilityClass
public class Parameters {
    public static boolean isRequired(Parameter parameter) {
        if(parameter.isAnnotationPresent(Nullable.class)) {
            return false;
        }
        if(parameter.isAnnotationPresent(Schema.class) && parameter.getAnnotation(Schema.class).requiredMode() == Schema.RequiredMode.NOT_REQUIRED) {
            return false;
        }
        return true;
    }

    public static String descriptionOf(Parameter parameter) {
        return ofNullable(parameter.getAnnotation(Schema.class))
                .map(Schema::description)
                .map(StringUtils::trimToNull)
                .orElse(null);
    }

    public static String titleOf(Parameter parameter) {
        return ofNullable(parameter.getAnnotation(Schema.class))
                .map(Schema::title)
                .map(StringUtils::trimToNull)
                .orElseGet(() -> Names.capitalizedWords(parameter.getName()));
    }
}
