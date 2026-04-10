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
package com.callibrity.mocapi.compat;

import com.callibrity.mocapi.model.BlobResourceContents;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.resources.McpResource;
import com.callibrity.mocapi.resources.McpResourceTemplate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CompatibilityResources {

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
  public McpResource staticTextResource() {
    return new McpResource() {
      @Override
      public Resource descriptor() {
        return new Resource(
            "test://static-text",
            "Static Text Resource",
            "A static text resource for conformance testing",
            "text/plain");
      }

      @Override
      public ReadResourceResult read() {
        var d = descriptor();
        return new ReadResourceResult(
            List.of(
                new TextResourceContents(
                    d.uri(), d.mimeType(), "This is the content of the static text resource.")));
      }
    };
  }

  @Bean
  public McpResource staticBinaryResource() {
    return new McpResource() {
      @Override
      public Resource descriptor() {
        return new Resource(
            "test://static-binary",
            "Static Binary Resource",
            "A static binary resource for conformance testing",
            "image/png");
      }

      @Override
      public ReadResourceResult read() {
        var d = descriptor();
        return new ReadResourceResult(
            List.of(new BlobResourceContents(d.uri(), d.mimeType(), TINY_PNG)));
      }
    };
  }

  @Bean
  public McpResource watchedResource() {
    return new McpResource() {
      @Override
      public Resource descriptor() {
        return new Resource(
            "test://watched-resource",
            "Watched Resource",
            "A resource that supports subscriptions for conformance testing",
            "text/plain");
      }

      @Override
      public ReadResourceResult read() {
        var d = descriptor();
        return new ReadResourceResult(
            List.of(
                new TextResourceContents(
                    d.uri(), d.mimeType(), "This is the content of the watched resource.")));
      }
    };
  }

  @Bean
  public McpResourceTemplate templateResource() {
    return new McpResourceTemplate() {
      @Override
      public ResourceTemplate descriptor() {
        return new ResourceTemplate(
            "test://template/{id}/data",
            "Template Resource",
            "A resource template for conformance testing",
            "application/json");
      }

      @Override
      public ReadResourceResult read(Map<String, String> pathVariables) {
        String id = pathVariables.get("id");
        String json =
            String.format(
                "{\"id\":\"%s\",\"templateTest\":true,\"data\":\"Data for ID: %s\"}", id, id);
        String uri = String.format("test://template/%s/data", id);
        return new ReadResourceResult(
            List.of(new TextResourceContents(uri, descriptor().mimeType(), json)));
      }
    };
  }
}
