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
package com.callibrity.mocapi.tasks.substrate;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.BooleanSchema;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.InputRequest;
import com.callibrity.mocapi.model.LegacyTitledEnumSchema;
import com.callibrity.mocapi.model.NumberSchema;
import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.StringSchema;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves a maximally populated {@link TaskRecord} — polymorphic content blocks, sealed {@link
 * InputRequest} variants, ledger entries, error detail, and raw {@code JsonNode} arguments —
 * survives the codec-jackson byte round-trip with full equality.
 */
class TaskRecordRoundTripTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @SuppressWarnings(
      "deprecation") // Exercises the deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility (docs/plans/2026-07-28-schema.ts) — the "type":"string" collision case that
  // PrimitiveSchemaDefinitionDeserializer must route correctly.
  private static RequestedSchema populatedRequestedSchema() {
    Map<String, PrimitiveSchemaDefinition> properties = new LinkedHashMap<>();
    properties.put("city", new StringSchema("City", "Destination city", null, null, null, null));
    properties.put("travelers", new NumberSchema("integer", "Travelers", null, 1, 10, 1));
    properties.put("confirmed", new BooleanSchema("Confirmed", null, null));
    properties.put(
        "status",
        new LegacyTitledEnumSchema(
            "Status", null, List.of("pending", "approved"), List.of("Pending", "Approved"), null));
    return new RequestedSchema(properties, List.of("city"), null);
  }

  @Test
  void maximallyPopulatedRecordRoundTrips() {
    TaskStore store = newStore();
    Instant createdAt = Instant.parse("2026-08-03T00:00:00Z");
    ObjectNode arguments = MAPPER.createObjectNode().put("city", "Cincinnati");
    ObjectNode answer = MAPPER.createObjectNode().put("confirmed", true);
    TaskRecord rec =
        new TaskRecord(
            "rt-1",
            "demo.tool",
            arguments,
            "user-1",
            "2026-07-28",
            null,
            TaskStatus.INPUT_REQUIRED,
            "waiting on slot-2",
            createdAt,
            createdAt.plusSeconds(5),
            Duration.ofHours(1),
            Duration.ofSeconds(2),
            List.of(
                new ResponseLedgerEntry(
                    "slot-1", "fp-1", new ElicitResult(ElicitAction.ACCEPT, answer)),
                new ResponseLedgerEntry("slot-2", "fp-2", null)),
            Map.of(
                "slot-2",
                new ElicitRequest(
                    new ElicitRequestFormParams("Confirm the city", populatedRequestedSchema()))),
            new CallToolResult(List.of(new TextContent("done", null)), false, null, null),
            new JsonRpcErrorDetail(-32000, "boom"),
            3L);

    store.create(rec);

    assertThat(store.get("rt-1")).contains(rec);
  }

  private TaskStore newStore() {
    CodecFactory codecFactory = new JacksonCodecFactory(MAPPER);
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new InMemoryAtomSpi(),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(
        atomFactory,
        Clock.fixed(Instant.parse("2026-08-03T00:00:10Z"), ZoneOffset.UTC),
        "mocapi:tasks:");
  }
}
