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
package com.callibrity.mocapi.example;

import com.callibrity.mocapi.example.tools.HelloTool;
import com.callibrity.mocapi.example.tools.Rot13Tool;
import com.callibrity.mocapi.example.tools.WeatherTool;
import com.callibrity.mocapi.tools.McpToolProvider;
import com.callibrity.mocapi.tools.annotation.AnnotationMcpToolProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MocapiExampleApplicationConfig {

// -------------------------- OTHER METHODS --------------------------

    @Bean
    public McpToolProvider rot13ToolsProvider(AnnotationMcpToolProviderFactory factory, Rot13Tool rot13Tool) {
        return factory.create(rot13Tool);
    }

    @Bean
    public McpToolProvider weatherToolsProvider(AnnotationMcpToolProviderFactory factory, WeatherTool weatherTool) {
        return factory.create(weatherTool);
    }

    @Bean
    public McpToolProvider helloToolsProvider(AnnotationMcpToolProviderFactory factory, HelloTool helloTool) {
        return factory.create(helloTool);
    }

}
