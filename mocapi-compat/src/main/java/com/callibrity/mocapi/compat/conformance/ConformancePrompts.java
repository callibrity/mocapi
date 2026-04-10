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
package com.callibrity.mocapi.compat.conformance;

import com.callibrity.mocapi.model.EmbeddedResource;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.ImageContent;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.prompts.McpPrompt;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConformancePrompts {

  // 1x1 red pixel PNG
  private static final String TINY_PNG =
      Base64.getEncoder()
          .encodeToString(
              new byte[] {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                (byte) 0x90,
                0x77,
                0x53,
                (byte) 0xDE,
                0x00,
                0x00,
                0x00,
                0x0C,
                0x49,
                0x44,
                0x41,
                0x54,
                0x08,
                (byte) 0xD7,
                0x63,
                (byte) 0xF8,
                (byte) 0xCF,
                (byte) 0xC0,
                0x00,
                0x00,
                0x00,
                0x02,
                0x00,
                0x01,
                (byte) 0xE2,
                0x21,
                (byte) 0xBC,
                0x33,
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4E,
                0x44,
                (byte) 0xAE,
                0x42,
                0x60,
                (byte) 0x82
              });

  @Bean
  public McpPrompt simplePrompt() {
    return new McpPrompt() {
      @Override
      public Prompt descriptor() {
        return new Prompt("test_simple_prompt", null, "A simple test prompt", null, List.of());
      }

      @Override
      public GetPromptResult get(Map<String, String> arguments) {
        return new GetPromptResult(
            "A simple test prompt",
            List.of(
                new PromptMessage(
                    Role.USER,
                    List.of(new TextContent("This is a simple prompt for testing.", null)))));
      }
    };
  }

  @Bean
  public McpPrompt promptWithArguments() {
    return new McpPrompt() {
      @Override
      public Prompt descriptor() {
        return new Prompt(
            "test_prompt_with_arguments",
            null,
            "A test prompt with arguments",
            null,
            List.of(
                new PromptArgument("arg1", "First argument", true),
                new PromptArgument("arg2", "Second argument", true)));
      }

      @Override
      public GetPromptResult get(Map<String, String> arguments) {
        String arg1 = arguments != null ? arguments.getOrDefault("arg1", "") : "";
        String arg2 = arguments != null ? arguments.getOrDefault("arg2", "") : "";
        return new GetPromptResult(
            "A test prompt with arguments",
            List.of(
                new PromptMessage(
                    Role.USER,
                    List.of(
                        new TextContent(
                            String.format(
                                "Prompt with arguments: arg1='%s', arg2='%s'", arg1, arg2),
                            null)))));
      }
    };
  }

  @Bean
  public McpPrompt promptWithEmbeddedResource() {
    return new McpPrompt() {
      @Override
      public Prompt descriptor() {
        return new Prompt(
            "test_prompt_with_embedded_resource",
            null,
            "A test prompt with an embedded resource",
            null,
            List.of(new PromptArgument("resourceUri", "URI of the resource to embed", true)));
      }

      @Override
      public GetPromptResult get(Map<String, String> arguments) {
        String resourceUri = arguments != null ? arguments.getOrDefault("resourceUri", "") : "";
        return new GetPromptResult(
            "A test prompt with an embedded resource",
            List.of(
                new PromptMessage(
                    Role.USER,
                    List.of(
                        new EmbeddedResource(
                            new TextResourceContents(
                                resourceUri,
                                "text/plain",
                                "Embedded resource content for testing."),
                            null))),
                new PromptMessage(
                    Role.USER,
                    List.of(
                        new TextContent("Please process the embedded resource above.", null)))));
      }
    };
  }

  @Bean
  public McpPrompt promptWithImage() {
    return new McpPrompt() {
      @Override
      public Prompt descriptor() {
        return new Prompt(
            "test_prompt_with_image", null, "A test prompt with an image", null, List.of());
      }

      @Override
      public GetPromptResult get(Map<String, String> arguments) {
        return new GetPromptResult(
            "A test prompt with an image",
            List.of(
                new PromptMessage(
                    Role.USER, List.of(new ImageContent(TINY_PNG, "image/png", null))),
                new PromptMessage(
                    Role.USER, List.of(new TextContent("Please analyze the image above.", null)))));
      }
    };
  }
}
