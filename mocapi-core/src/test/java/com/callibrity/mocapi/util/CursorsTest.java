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
package com.callibrity.mocapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CursorsTest {

  @Test
  void encodeAndDecodeRoundTrip() {
    assertThat(Cursors.decode(Cursors.encode(0))).isZero();
    assertThat(Cursors.decode(Cursors.encode(1))).isEqualTo(1);
    assertThat(Cursors.decode(Cursors.encode(42))).isEqualTo(42);
    assertThat(Cursors.decode(Cursors.encode(1000))).isEqualTo(1000);
    assertThat(Cursors.decode(Cursors.encode(Integer.MAX_VALUE))).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void nullCursorReturnsZero() {
    assertThat(Cursors.decode(null)).isZero();
  }

  @Test
  void encodedCursorIsFixedLength() {
    // 4 bytes → 8 Base64 characters (with padding)
    assertThat(Cursors.encode(0)).hasSize(8);
    assertThat(Cursors.encode(1)).hasSize(8);
    assertThat(Cursors.encode(Integer.MAX_VALUE)).hasSize(8);
  }

  @Test
  void invalidBase64Throws() {
    assertThatThrownBy(() -> Cursors.decode("not-valid-base64!!!"))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void tamperedCursorThrows() {
    assertThatThrownBy(() -> Cursors.decode("AAAA"))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void emptyCursorThrows() {
    assertThatThrownBy(() -> Cursors.decode(""))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  // --- paginate tests ---

  private static final List<String> ITEMS = List.of("a", "b", "c", "d", "e");

  @Test
  void firstPageWithNullCursor() {
    var page = Cursors.paginate(ITEMS, null, 2);
    assertThat(page.items()).containsExactly("a", "b");
    assertThat(page.nextCursor()).isNotNull();
  }

  @Test
  void secondPageWithCursor() {
    var first = Cursors.paginate(ITEMS, null, 2);
    var second = Cursors.paginate(ITEMS, first.nextCursor(), 2);
    assertThat(second.items()).containsExactly("c", "d");
    assertThat(second.nextCursor()).isNotNull();
  }

  @Test
  void lastPageHasNullCursor() {
    var first = Cursors.paginate(ITEMS, null, 2);
    var second = Cursors.paginate(ITEMS, first.nextCursor(), 2);
    var third = Cursors.paginate(ITEMS, second.nextCursor(), 2);
    assertThat(third.items()).containsExactly("e");
    assertThat(third.nextCursor()).isNull();
  }

  @Test
  void pageSizeLargerThanListReturnsAll() {
    var page = Cursors.paginate(ITEMS, null, 100);
    assertThat(page.items()).containsExactly("a", "b", "c", "d", "e");
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void emptyListReturnsEmptyPage() {
    var page = Cursors.paginate(List.of(), null, 10);
    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void invalidBase64InPaginateThrows() {
    assertThatThrownBy(() -> Cursors.paginate(ITEMS, "!!!not-base64!!!", 2))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void shortBase64InPaginateGetsClamped() {
    // "AA" decodes to 1 byte, too short for getInt() — throws, caught, re-thrown
    assertThatThrownBy(() -> Cursors.paginate(ITEMS, "AA", 2))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void outOfRangeCursorClampsToEnd() {
    var page = Cursors.paginate(ITEMS, Cursors.encode(100), 2);
    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void negativeCursorClampsToZero() {
    var page = Cursors.paginate(ITEMS, Cursors.encode(-5), 2);
    assertThat(page.items()).containsExactly("a", "b");
  }
}
