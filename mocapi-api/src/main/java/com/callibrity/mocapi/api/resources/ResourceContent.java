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
package com.callibrity.mocapi.api.resources;

/**
 * How a {@link McpResource} method's payload should be represented on the wire when the return type
 * does not itself decide (i.e. a {@code org.springframework.core.io.Resource} of opaque bytes).
 * String/{@code CharSequence} returns are always text and {@code byte[]}/{@code ByteBuffer} returns
 * are always blob regardless of this setting.
 */
public enum ResourceContent {

  /**
   * Infer text vs. blob from the resource's declared {@code mimeType}: a {@code text/*} base type,
   * or a {@code json}/{@code xml}/{@code javascript}/{@code ecmascript} subtype (including {@code
   * +json}/{@code +xml} structured suffixes), is treated as text; anything else — including a
   * blank/unknown/malformed mime type — is treated as blob.
   */
  AUTO,

  /**
   * Force text: decode the bytes as UTF-8 (honoring a {@code charset} mime parameter if present).
   */
  TEXT,

  /** Force blob: base64-encode the bytes. */
  BLOB
}
