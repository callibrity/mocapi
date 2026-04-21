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
package com.callibrity.mocapi.oauth2.metadata;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.Implementation;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceNameMetadataCustomizerTest {

  @Test
  void prefers_implementation_title_when_present() {
    Implementation impl = new Implementation("mocapi", "Mocapi Server", "1.0.0");
    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);

    new ResourceNameMetadataCustomizer(impl).customize(builder);

    verify(builder).resourceName("Mocapi Server");
  }

  @Test
  void falls_back_to_implementation_name_when_title_blank() {
    Implementation impl = new Implementation("mocapi", "", "1.0.0");
    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);

    new ResourceNameMetadataCustomizer(impl).customize(builder);

    verify(builder).resourceName("mocapi");
  }

  @Test
  void contributes_nothing_when_both_title_and_name_blank() {
    Implementation impl = new Implementation("", "", "1.0.0");
    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);

    new ResourceNameMetadataCustomizer(impl).customize(builder);

    verify(builder, never()).resourceName(anyString());
  }
}
