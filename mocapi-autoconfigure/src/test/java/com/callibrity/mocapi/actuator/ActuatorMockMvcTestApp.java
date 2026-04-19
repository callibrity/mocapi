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
package com.callibrity.mocapi.actuator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerPromptsAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerResourcesAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.SubstrateOrderingAutoConfiguration;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.List;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Minimal Spring Boot test application for the MockMvc exposure tests. Excludes the mocapi-server
 * autoconfigs (they need Substrate {@code AtomFactory} and a full handler graph), while keeping the
 * standard web + actuator autoconfigs so {@code /actuator/mcp} is actually mapped and reachable.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = {
      SubstrateOrderingAutoConfiguration.class,
      MocapiServerToolsAutoConfiguration.class,
      MocapiServerPromptsAutoConfiguration.class,
      MocapiServerResourcesAutoConfiguration.class,
      MocapiServerAutoConfiguration.class
    })
public class ActuatorMockMvcTestApp {

  @Bean
  Implementation serverInfo() {
    return new Implementation("mocapi", "Mocapi", "9.9.9");
  }

  @Bean
  McpToolsService toolsService() {
    McpToolsService m = mock(McpToolsService.class);
    when(m.allDescriptors())
        .thenReturn(List.of(new Tool("sample_tool", "Sample", "desc", null, null)));
    return m;
  }

  @Bean
  McpPromptsService promptsService() {
    McpPromptsService m = mock(McpPromptsService.class);
    when(m.allDescriptors())
        .thenReturn(List.of(new Prompt("sample_prompt", null, null, null, null)));
    return m;
  }

  @Bean
  McpResourcesService resourcesService() {
    McpResourcesService m = mock(McpResourcesService.class);
    when(m.allResourceDescriptors()).thenReturn(List.of(new Resource("docs://a", "a", null, null)));
    when(m.allResourceTemplateDescriptors())
        .thenReturn(List.of(new ResourceTemplate("docs://{x}", "x", null, null)));
    return m;
  }
}
