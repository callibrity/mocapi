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

import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import org.springframework.stereotype.Component;

@Component
@ResourceService
public class CompatibilityResources {

  // 1x1 red pixel PNG
  private static final byte[] TINY_PNG = {
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
  };

  @ResourceMethod(
      uri = "test://static-text",
      name = "Static Text Resource",
      description = "A static text resource for conformance testing",
      mimeType = "text/plain")
  public ReadResourceResult staticText() {
    return ReadResourceResult.ofText(
        "test://static-text", "text/plain", "This is the content of the static text resource.");
  }

  @ResourceMethod(
      uri = "test://static-binary",
      name = "Static Binary Resource",
      description = "A static binary resource for conformance testing",
      mimeType = "image/png")
  public ReadResourceResult staticBinary() {
    return ReadResourceResult.ofBlob("test://static-binary", "image/png", TINY_PNG);
  }

  @ResourceMethod(
      uri = "test://watched-resource",
      name = "Watched Resource",
      description = "A resource that supports subscriptions for conformance testing",
      mimeType = "text/plain")
  public ReadResourceResult watched() {
    return ReadResourceResult.ofText(
        "test://watched-resource", "text/plain", "This is the content of the watched resource.");
  }

  @ResourceTemplateMethod(
      uriTemplate = "test://template/{id}/data",
      name = "Template Resource",
      description = "A resource template for conformance testing",
      mimeType = "application/json")
  public ReadResourceResult templateData(String id) {
    String json =
        String.format("{\"id\":\"%s\",\"templateTest\":true,\"data\":\"Data for ID: %s\"}", id, id);
    return ReadResourceResult.ofText(
        String.format("test://template/%s/data", id), "application/json", json);
  }
}
