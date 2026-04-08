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
package com.callibrity.mocapi.autoconfigure;

import com.callibrity.mocapi.server.ServerInfo;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

@ConfigurationProperties(prefix = "mocapi")
@Data
public class MocapiProperties {

  private ServerInfo serverInfo;
  private String instructions;
  private List<String> allowedOrigins = List.of("localhost", "127.0.0.1", "[::1]");
  private Duration sessionTimeout = Duration.ofHours(1);

  private EventId eventId = new EventId();

  private Elicitation elicitation = new Elicitation();

  private Pagination pagination = new Pagination();

  @Data
  public static class EventId {
    /** Base64-encoded 32-byte master key for event ID encryption. */
    @Nullable private String masterKey;
  }

  @Data
  public static class Elicitation {
    /** How long to wait for the client to respond to an elicitation request. */
    private Duration timeout = Duration.ofMinutes(5);
  }

  @Data
  public static class Pagination {
    private int pageSize = 50;
  }
}
