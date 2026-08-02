/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceContributorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static MrtrElicitationEngine engine() {
    return new MrtrElicitationEngine(
        RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, MAPPER), MAPPER);
  }

  private static ReadResourceHandler readerOnly(
      String uri, String text, java.util.List<com.callibrity.mocapi.server.guards.Guard> guards) {
    return new ReadResourceHandler(
        new Resource(uri, uri, uri, "text/plain"),
        guards,
        () -> ReadResourceResult.ofText(uri, "text/plain", text));
  }

  private static ResourceContributor contributor(ReadResourceHandler... handlers) {
    return new ResourceContributor() {
      @Override
      public List<ReadResourceHandler> resources() {
        return List.of(handlers);
      }
    };
  }

  @Test
  void reader_only_handler_describe_has_no_class_or_method() {
    var handler = readerOnly("ui://a", "A", List.of());

    var descriptor = handler.describe();

    assertThat(descriptor.kind()).isEqualTo(HandlerKind.RESOURCE);
    assertThat(descriptor.declaringClassName()).isNull();
    assertThat(descriptor.methodName()).isNull();
    assertThat(handler.method()).isNull();
    assertThat(handler.bean()).isNull();
  }

  @Test
  void service_merges_resources_from_every_contributor() {
    var service =
        new McpResourcesService(
            List.of(
                contributor(readerOnly("ui://a", "A", List.of())),
                contributor(readerOnly("ui://b", "B", List.of()))),
            engine(),
            50,
            CacheSettings.defaults(),
            List.of());

    var listed = service.listResources(null).resources().stream().map(Resource::uri).toList();
    assertThat(listed).containsExactly("ui://a", "ui://b");

    var read =
        (ReadResourceResult)
            service.readResource(new ResourceRequestParams("ui://b", null, null, null));
    var content = (TextResourceContents) read.contents().getFirst();
    assertThat(content.text()).isEqualTo("B");
  }

  @Test
  void denied_guard_hides_a_reader_only_resource_from_the_list() {
    var denied = readerOnly("ui://secret", "S", List.of(() -> new GuardDecision.Deny("nope")));
    var service =
        new McpResourcesService(
            List.of(contributor(denied)), engine(), 50, CacheSettings.defaults(), List.of());

    assertThat(service.listResources(null).resources()).isEmpty();
  }
}
