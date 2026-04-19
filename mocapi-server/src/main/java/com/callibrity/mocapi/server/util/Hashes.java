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
package com.callibrity.mocapi.server.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hashing helpers for mocapi. All methods produce lowercase hex with a {@code sha256:} prefix so
 * the algorithm is self-describing and values compare cleanly against external tooling (git,
 * sha256sum, Docker image digests).
 *
 * <p>SHA-256 is mandated by the Java platform, so the underlying {@link
 * java.security.NoSuchAlgorithmException} is effectively unreachable; callers see an unchecked
 * {@link IllegalStateException} if a non-conformant JVM ever omits it.
 */
public final class Hashes {

  private static final String ALGORITHM = "SHA-256";
  private static final String PREFIX = "sha256:";

  private Hashes() {}

  /** SHA-256 of the UTF-8 bytes of {@code content}, hex-encoded with {@code sha256:} prefix. */
  public static String sha256Of(String content) {
    Objects.requireNonNull(content, "content");
    return sha256Of(content.getBytes(StandardCharsets.UTF_8));
  }

  /** SHA-256 of {@code bytes}, hex-encoded with {@code sha256:} prefix. */
  public static String sha256Of(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return PREFIX + HexFormat.of().formatHex(sha256Bytes(bytes));
  }

  /**
   * SHA-256 of the concatenation of the given chunks' UTF-8 bytes, hex-encoded with {@code sha256:}
   * prefix. Useful for hashing composite inputs (handler-name + arguments, session-id + cursor
   * position, etc.) without an intermediate String concat. No delimiter is inserted between chunks.
   *
   * @throws NullPointerException if {@code chunks} is null or any element is null
   */
  public static String sha256Of(String... chunks) {
    Objects.requireNonNull(chunks, "chunks");
    MessageDigest digest = newDigest();
    for (int i = 0; i < chunks.length; i++) {
      String chunk = chunks[i];
      if (chunk == null) {
        throw new NullPointerException("chunks[" + i + "]");
      }
      digest.update(chunk.getBytes(StandardCharsets.UTF_8));
    }
    return PREFIX + HexFormat.of().formatHex(digest.digest());
  }

  private static byte[] sha256Bytes(byte[] bytes) {
    return newDigest().digest(bytes);
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance(ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(ALGORITHM + " not available", e);
    }
  }
}
