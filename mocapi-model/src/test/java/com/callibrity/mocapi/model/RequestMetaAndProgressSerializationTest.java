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
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RequestMetaAndProgressSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void requestMetaWithStringToken() throws Exception {
    var meta = new RequestMeta("token-abc");
    String json = mapper.writeValueAsString(meta);
    assertThat(json).isEqualTo("{\"progressToken\":\"token-abc\"}");

    var deserialized = mapper.readValue(json, RequestMeta.class);
    assertThat(deserialized.progressToken()).isEqualTo("token-abc");
  }

  @Test
  void requestMetaWithNumericToken() throws Exception {
    String json = "{\"progressToken\":42}";
    var deserialized = mapper.readValue(json, RequestMeta.class);
    assertThat(deserialized.progressToken()).isEqualTo(42);
  }

  @Test
  void requestMetaNullTokenOmitted() throws Exception {
    var meta = new RequestMeta(null);
    String json = mapper.writeValueAsString(meta);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void progressNotificationParamsRoundTrip() throws Exception {
    var params = new ProgressNotificationParams("token-1", 0.5, 1.0, "halfway");
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"progressToken\":\"token-1\"");
    assertThat(json).contains("\"progress\":0.5");
    assertThat(json).contains("\"total\":1.0");
    assertThat(json).contains("\"message\":\"halfway\"");

    var deserialized = mapper.readValue(json, ProgressNotificationParams.class);
    assertThat(deserialized.progressToken()).isEqualTo("token-1");
    assertThat(deserialized.progress()).isEqualTo(0.5);
    assertThat(deserialized.total()).isEqualTo(1.0);
    assertThat(deserialized.message()).isEqualTo("halfway");
  }

  @Test
  void progressNotificationParamsWithNumericToken() throws Exception {
    String json = "{\"progressToken\":99,\"progress\":0.75}";
    var deserialized = mapper.readValue(json, ProgressNotificationParams.class);
    assertThat(deserialized.progressToken()).isEqualTo(99);
    assertThat(deserialized.progress()).isEqualTo(0.75);
  }

  @Test
  void progressNotificationParamsNullFieldsOmitted() throws Exception {
    var params = new ProgressNotificationParams("t", 0.0, null, null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).doesNotContain("total");
    assertThat(json).doesNotContain("message");
  }

  @Test
  void progressNotificationRoundTrip() throws Exception {
    var params = new ProgressNotificationParams("tok", 0.3, 1.0, "working");
    var notification = new ProgressNotification(ProgressNotification.METHOD, params);
    String json = mapper.writeValueAsString(notification);
    assertThat(json).contains("\"method\":\"notifications/progress\"");
    assertThat(json).contains("\"params\":");

    var deserialized = mapper.readValue(json, ProgressNotification.class);
    assertThat(deserialized.method()).isEqualTo("notifications/progress");
    assertThat(deserialized.params().progressToken()).isEqualTo("tok");
    assertThat(deserialized.params().progress()).isEqualTo(0.3);
  }
}
