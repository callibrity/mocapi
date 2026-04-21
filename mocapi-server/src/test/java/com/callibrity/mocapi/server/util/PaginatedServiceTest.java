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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaginatedServiceTest {

  private record Item(String id, String label) {}

  private static final class ItemService extends PaginatedService<Item, String> {
    ItemService(List<Item> items) {
      super(items, Item::id, Item::label, Comparator.naturalOrder(), "Item", 2);
    }
  }

  private final ItemService service =
      new ItemService(
          List.of(new Item("b", "Beta"), new Item("a", "Alpha"), new Item("c", "Gamma")));

  @Test
  void allItems_returns_handlers_sorted_by_descriptor_comparator() {
    assertThat(service.allItems())
        .extracting(Item::id)
        .containsExactly("a", "b", "c"); // sorted by descriptor (label): Alpha, Beta, Gamma
  }

  @Test
  void allDescriptors_maps_sorted_handlers_through_descriptor_extractor() {
    assertThat(service.allDescriptors()).containsExactly("Alpha", "Beta", "Gamma");
  }

  @Test
  void findByName_returns_present_when_id_matches() {
    assertThat(service.findByName("a")).map(Item::label).hasValue("Alpha");
  }

  @Test
  void findByName_returns_empty_when_id_unknown() {
    assertThat(service.findByName("zzz")).isEmpty();
  }

  @Test
  void lookup_throws_invalid_params_when_id_unknown() {
    assertThatThrownBy(() -> service.lookup("zzz"))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Item zzz not found");
  }
}
