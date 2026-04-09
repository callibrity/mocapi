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

import com.callibrity.mocapi.resources.McpResource;
import com.callibrity.mocapi.resources.McpResourceProvider;
import com.callibrity.mocapi.resources.McpResourceTemplate;
import com.callibrity.mocapi.resources.ReadResourceResponse;
import com.callibrity.mocapi.resources.ResourceContent;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ConformanceResources implements McpResourceProvider {

  private static final String STATIC_TEXT_URI = "test://static-text";
  private static final String STATIC_BINARY_URI = "test://static-binary";
  private static final String WATCHED_RESOURCE_URI = "test://watched-resource";
  private static final String TEMPLATE_URI_TEMPLATE = "test://template/{id}/data";
  private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^test://template/([^/]+)/data$");

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

  @Override
  public List<McpResource> getResources() {
    return List.of(
        new McpResource(
            STATIC_TEXT_URI,
            "Static Text Resource",
            "A static text resource for conformance testing",
            "text/plain"),
        new McpResource(
            STATIC_BINARY_URI,
            "Static Binary Resource",
            "A static binary resource for conformance testing",
            "image/png"),
        new McpResource(
            WATCHED_RESOURCE_URI,
            "Watched Resource",
            "A resource that supports subscriptions for conformance testing",
            "text/plain"));
  }

  @Override
  public List<McpResourceTemplate> getResourceTemplates() {
    return List.of(
        new McpResourceTemplate(
            TEMPLATE_URI_TEMPLATE,
            "Template Resource",
            "A resource template for conformance testing",
            "application/json"));
  }

  @Override
  public ReadResourceResponse read(String uri) {
    if (STATIC_TEXT_URI.equals(uri)) {
      return new ReadResourceResponse(
          List.of(
              new ResourceContent(
                  STATIC_TEXT_URI,
                  "text/plain",
                  "This is the content of the static text resource.",
                  null)));
    }
    if (STATIC_BINARY_URI.equals(uri)) {
      return new ReadResourceResponse(
          List.of(new ResourceContent(STATIC_BINARY_URI, "image/png", null, TINY_PNG)));
    }
    if (WATCHED_RESOURCE_URI.equals(uri)) {
      return new ReadResourceResponse(
          List.of(
              new ResourceContent(
                  WATCHED_RESOURCE_URI,
                  "text/plain",
                  "This is the content of the watched resource.",
                  null)));
    }
    Matcher matcher = TEMPLATE_PATTERN.matcher(uri);
    if (matcher.matches()) {
      String id = matcher.group(1);
      String json =
          String.format(
              "{\"id\":\"%s\",\"templateTest\":true,\"data\":\"Data for ID: %s\"}", id, id);
      return new ReadResourceResponse(
          List.of(new ResourceContent(uri, "application/json", json, null)));
    }
    return null;
  }
}
