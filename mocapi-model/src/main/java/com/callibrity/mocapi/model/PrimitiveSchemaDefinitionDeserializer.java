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
package com.callibrity.mocapi.model;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Routes {@code PrimitiveSchemaDefinition} JSON payloads to the correct sealed-hierarchy leaf type.
 *
 * <p>{@code @JsonTypeInfo}-style discrimination cannot work here: {@link StringSchema}, {@link
 * UntitledSingleSelectEnumSchema}, {@link TitledSingleSelectEnumSchema}, and (deprecated) {@link
 * LegacyTitledEnumSchema} all share the wire discriminator {@code "type": "string"}. The only way
 * to tell them apart is by which other properties are present on the object, so this deserializer
 * buffers the node and inspects its shape before delegating concrete binding back to the codec via
 * {@link DeserializationContext#readTreeAsValue(JsonNode, Class)} (which keeps every record's own
 * {@code @JsonProperty} annotations in effect).
 *
 * <p>Routing table (see {@code docs/plans/2026-07-28-schema.ts}):
 *
 * <ul>
 *   <li>{@code type: "boolean"} → {@link BooleanSchema}
 *   <li>{@code type: "number" | "integer"} → {@link NumberSchema}
 *   <li>{@code type: "array"} → {@link TitledMultiSelectEnumSchema} if {@code items.anyOf} is
 *       present, else {@link UntitledMultiSelectEnumSchema}
 *   <li>{@code type: "string"} with {@code oneOf} → {@link TitledSingleSelectEnumSchema}
 *   <li>{@code type: "string"} with {@code enum} and {@code enumNames} → {@link
 *       LegacyTitledEnumSchema} (the one wire signal that distinguishes it from the untitled
 *       single-select shape below — a legacy payload with no {@code enumNames} is genuinely
 *       indistinguishable from {@link UntitledSingleSelectEnumSchema} and is deserialized as such;
 *       this ambiguity exists in the MCP wire format itself, not in this routing)
 *   <li>{@code type: "string"} with {@code enum} but no {@code enumNames} → {@link
 *       UntitledSingleSelectEnumSchema}
 *   <li>{@code type: "string"} with neither → {@link StringSchema}
 *   <li>anything else → a {@code MismatchedInputException} naming the offending {@code type} value
 * </ul>
 */
final class PrimitiveSchemaDefinitionDeserializer
    extends StdDeserializer<PrimitiveSchemaDefinition> {

  PrimitiveSchemaDefinitionDeserializer() {
    super(PrimitiveSchemaDefinition.class);
  }

  @Override
  public PrimitiveSchemaDefinition deserialize(JsonParser p, DeserializationContext ctxt) {
    JsonNode node = ctxt.readTree(p);
    JsonNode typeNode = node.get("type");
    if (typeNode == null || !typeNode.isString()) {
      return ctxt.reportInputMismatch(
          PrimitiveSchemaDefinition.class,
          "PrimitiveSchemaDefinition requires a string \"type\" property; found: %s",
          typeNode);
    }
    return switch (typeNode.asString()) {
      case "boolean" -> ctxt.readTreeAsValue(node, BooleanSchema.class);
      case "number", "integer" -> ctxt.readTreeAsValue(node, NumberSchema.class);
      case "string" -> deserializeStringVariant(node, ctxt);
      case "array" -> deserializeArrayVariant(node, ctxt);
      default ->
          ctxt.reportInputMismatch(
              PrimitiveSchemaDefinition.class,
              "Unknown PrimitiveSchemaDefinition \"type\" value: \"%s\"",
              typeNode.asString());
    };
  }

  @SuppressWarnings(
      "deprecation") // LegacyTitledEnumSchema is a spec-mandated MCP backward-compatibility
  // variant (docs/plans/2026-07-28-schema.ts) that this router must still be able to produce
  private PrimitiveSchemaDefinition deserializeStringVariant(
      JsonNode node, DeserializationContext ctxt) {
    if (node.has("oneOf")) {
      return ctxt.readTreeAsValue(node, TitledSingleSelectEnumSchema.class);
    }
    if (node.has("enum")) {
      return node.has("enumNames")
          ? ctxt.readTreeAsValue(node, LegacyTitledEnumSchema.class)
          : ctxt.readTreeAsValue(node, UntitledSingleSelectEnumSchema.class);
    }
    return ctxt.readTreeAsValue(node, StringSchema.class);
  }

  private PrimitiveSchemaDefinition deserializeArrayVariant(
      JsonNode node, DeserializationContext ctxt) {
    JsonNode items = node.get("items");
    if (items == null) {
      return ctxt.reportInputMismatch(
          PrimitiveSchemaDefinition.class,
          "PrimitiveSchemaDefinition with \"type\":\"array\" requires an \"items\" property");
    }
    return items.has("anyOf")
        ? ctxt.readTreeAsValue(node, TitledMultiSelectEnumSchema.class)
        : ctxt.readTreeAsValue(node, UntitledMultiSelectEnumSchema.class);
  }
}
