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

import com.callibrity.mocapi.actuator.McpActuatorSnapshot.PromptArgumentInfo;
import com.callibrity.mocapi.actuator.McpActuatorSnapshot.PromptInfo;
import com.callibrity.mocapi.actuator.McpActuatorSnapshot.ResourceInfo;
import com.callibrity.mocapi.actuator.McpActuatorSnapshot.ResourceTemplateInfo;
import com.callibrity.mocapi.actuator.McpActuatorSnapshot.ToolInfo;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/** Pure mapping helpers from mocapi descriptor records to the actuator snapshot shape. */
final class McpActuatorSnapshots {

  private McpActuatorSnapshots() {}

  static ToolInfo toToolInfo(Tool tool) {
    return new ToolInfo(
        tool.name(),
        tool.title(),
        tool.description(),
        schemaDigest(tool.inputSchema()),
        schemaDigest(tool.outputSchema()));
  }

  static PromptInfo toPromptInfo(Prompt prompt) {
    List<PromptArgumentInfo> arguments =
        prompt.arguments() == null
            ? null
            : prompt.arguments().stream()
                .map(a -> new PromptArgumentInfo(a.name(), a.required()))
                .toList();
    return new PromptInfo(prompt.name(), prompt.title(), prompt.description(), arguments);
  }

  static ResourceInfo toResourceInfo(Resource resource) {
    return new ResourceInfo(
        resource.uri(), resource.name(), resource.description(), resource.mimeType());
  }

  static ResourceTemplateInfo toResourceTemplateInfo(ResourceTemplate template) {
    return new ResourceTemplateInfo(
        template.uriTemplate(), template.name(), template.description(), template.mimeType());
  }

  // SHA-256 of the schema's JSON rendering. ObjectNode.toString() produces the same key ordering
  // every call (insertion order), so the digest is stable for a given schema instance — good
  // enough for "did this schema drift?" checks across deployments.
  static String schemaDigest(ObjectNode schema) {
    if (schema == null) {
      return null;
    }
    byte[] bytes = schema.toString().getBytes(StandardCharsets.UTF_8);
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      return "sha256:" + HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
