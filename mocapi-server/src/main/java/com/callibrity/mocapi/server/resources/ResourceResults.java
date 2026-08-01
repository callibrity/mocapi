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

import com.callibrity.mocapi.api.resources.ResourceContent;
import com.callibrity.mocapi.model.ReadResourceResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Converts a resource method's return value into a {@link ReadResourceResult} (ADR-0035). A method
 * may return the full {@code ReadResourceResult}, or a convenience payload that is wrapped here:
 *
 * <ul>
 *   <li>{@code String} / {@code CharSequence} → text
 *   <li>{@code byte[]} / {@code ByteBuffer} → blob (a {@code ByteBuffer} is read non-destructively
 *       via {@link ByteBuffer#duplicate()})
 *   <li>{@code org.springframework.core.io.Resource} → text or blob per the {@link ResourceContent}
 *       mode (a {@link ResourceContent#AUTO} resolved against the declared {@code mimeType})
 * </ul>
 */
public final class ResourceResults {

  private ResourceResults() {}

  /**
   * Wraps {@code value} as a {@link ReadResourceResult} for the resource identified by {@code uri}.
   *
   * @param value the method's return value; never {@code null}
   * @param uri the resource URI stamped into the produced contents
   * @param mimeType the resource's declared MIME type, or {@code null}
   * @param content the text/blob disambiguation mode for a {@code Resource} return
   */
  public static ReadResourceResult toResult(
      Object value, String uri, String mimeType, ResourceContent content) {
    if (value == null) {
      throw new IllegalStateException("Resource reader for " + uri + " returned null");
    }
    if (value instanceof ReadResourceResult result) {
      return result;
    }
    if (value instanceof CharSequence text) {
      return ReadResourceResult.ofText(uri, mimeType, text.toString());
    }
    if (value instanceof byte[] bytes) {
      return ReadResourceResult.ofBlob(uri, mimeType, bytes);
    }
    if (value instanceof ByteBuffer buffer) {
      return ReadResourceResult.ofBlob(uri, mimeType, toBytes(buffer));
    }
    if (value instanceof Resource resource) {
      return fromResource(resource, uri, mimeType, content);
    }
    throw new IllegalStateException(
        "Unsupported resource return value of type " + value.getClass().getName() + " for " + uri);
  }

  private static ReadResourceResult fromResource(
      Resource resource, String uri, String mimeType, ResourceContent content) {
    byte[] bytes = readBytes(resource);
    if (isText(content, mimeType)) {
      return ReadResourceResult.ofText(uri, mimeType, new String(bytes, charsetOf(mimeType)));
    }
    return ReadResourceResult.ofBlob(uri, mimeType, bytes);
  }

  private static byte[] readBytes(Resource resource) {
    try {
      return resource.getContentAsByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read resource " + resource.getDescription(), e);
    }
  }

  private static byte[] toBytes(ByteBuffer buffer) {
    ByteBuffer view = buffer.duplicate();
    byte[] bytes = new byte[view.remaining()];
    view.get(bytes);
    return bytes;
  }

  private static boolean isText(ResourceContent content, String mimeType) {
    return switch (content) {
      case TEXT -> true;
      case BLOB -> false;
      case AUTO -> isTextMime(mimeType);
    };
  }

  private static boolean isTextMime(String mimeType) {
    MimeType parsed = parse(mimeType);
    if (parsed == null) {
      return false;
    }
    String subtype = parsed.getSubtype();
    return "text".equalsIgnoreCase(parsed.getType())
        || "json".equalsIgnoreCase(subtype)
        || "xml".equalsIgnoreCase(subtype)
        || "javascript".equalsIgnoreCase(subtype)
        || "ecmascript".equalsIgnoreCase(subtype)
        || subtype.endsWith("+json")
        || subtype.endsWith("+xml");
  }

  private static Charset charsetOf(String mimeType) {
    MimeType parsed = parse(mimeType);
    if (parsed != null && parsed.getCharset() != null) {
      return parsed.getCharset();
    }
    return StandardCharsets.UTF_8;
  }

  private static MimeType parse(String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      return null;
    }
    try {
      return MimeTypeUtils.parseMimeType(mimeType);
    } catch (RuntimeException _) {
      return null;
    }
  }
}
