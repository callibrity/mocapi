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
package com.callibrity.mocapi.server.autoconfigure;

import com.callibrity.mocapi.server.session.AtomMcpSessionStore;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.mocapi.server.substrate.SubstrateTestSupport;
import java.time.Duration;

/**
 * Creates a real {@link McpSessionStore} backed by in-memory substrate primitives for testing. Same
 * code path as production when no backend module is on the classpath — no fakes, no mocks.
 */
public final class SessionStoreTestSupport {

  private SessionStoreTestSupport() {}

  public static McpSessionStore create() {
    return new AtomMcpSessionStore(SubstrateTestSupport.atomFactory(), Duration.ofHours(1));
  }
}
