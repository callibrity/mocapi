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
package com.callibrity.mocapi.tools.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface Tool {

    /**
     * The name of the tool. If not specified, a dot-separated, kebab-case class name and method name will be used. For
     * example, if the class is `com.example.MyTool` and the method is `doSomething`, the default name will be
     * `my-tool.do-something`.
     *
     * @return the name of the tool
     */
    String name() default "";

    /**
     * The title of the tool. This is a human-readable name that can be used in documentation or user interfaces.
     * If not specified, a space-separated, capitalized version of the class name and method name will
     * be used. For example, if the class is `com.example.MyTool` and the method is `doSomething`, the default title
     * will be `My Tool Do Something`.
     *
     * @return the title of the tool
     */
    String title() default "";

    /**
     * A description of the tool. This should provide a brief overview of what the tool does and how it can be used. If
     * not specified, the title will be used as the description.
     *
     * @return the description of the tool
     */
    String description() default "";
}
