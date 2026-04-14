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

import java.util.List;

public record ReadResourceResult(List<ResourceContents> contents) {

  /**
   * Builds a single-entry result wrapping a {@link TextResourceContents}.
   *
   * @param uri the resource URI
   * @param mimeType the content MIME type, or {@code null}
   * @param text the text content
   */
  public static ReadResourceResult ofText(String uri, String mimeType, String text) {
    return new ReadResourceResult(List.of(new TextResourceContents(uri, mimeType, text)));
  }

  /**
   * Builds a single-entry result wrapping a {@link BlobResourceContents}. The {@code blob} is
   * already base64-encoded; use {@link #ofBlob(String, String, byte[])} to encode raw bytes.
   *
   * @param uri the resource URI
   * @param mimeType the content MIME type, or {@code null}
   * @param blob the base64-encoded blob payload
   */
  public static ReadResourceResult ofBlob(String uri, String mimeType, String blob) {
    return new ReadResourceResult(List.of(new BlobResourceContents(uri, mimeType, blob)));
  }

  /**
   * Builds a single-entry result wrapping a {@link BlobResourceContents}, base64-encoding the
   * supplied bytes.
   *
   * @param uri the resource URI
   * @param mimeType the content MIME type, or {@code null}
   * @param bytes the raw binary payload
   */
  public static ReadResourceResult ofBlob(String uri, String mimeType, byte[] bytes) {
    return new ReadResourceResult(List.of(BlobResourceContents.of(uri, mimeType, bytes)));
  }
}
