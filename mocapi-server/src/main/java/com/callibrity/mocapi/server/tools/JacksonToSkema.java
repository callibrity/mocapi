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
package com.callibrity.mocapi.server.tools;

import com.github.erosb.jsonsKema.IJsonValue;
import com.github.erosb.jsonsKema.JsonArray;
import com.github.erosb.jsonsKema.JsonBoolean;
import com.github.erosb.jsonsKema.JsonNull;
import com.github.erosb.jsonsKema.JsonNumber;
import com.github.erosb.jsonsKema.JsonObject;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.JsonString;
import com.github.erosb.jsonsKema.JsonValue;
import com.github.erosb.jsonsKema.UnknownSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Converts an already-parsed Jackson {@link JsonNode} into the json-sKema {@link IJsonValue} tree
 * that {@code Validator.validate} consumes, directly — without serializing the node back to text
 * and re-parsing it through {@code JsonParser}.
 *
 * <p>The tool arguments reach the validating interceptor as a Jackson {@link JsonNode} (the request
 * was already parsed once by the transport). The prior implementation did {@code new
 * JsonParser(args.toString()).parse()}: a full re-serialization to a {@code String} followed by a
 * second parse into json-sKema's own value model. Under load that round trip was a measurable
 * hot-path cost (JFR: {@code args.toString()} plus {@code JsonParser.<init>} together ~5% of on-CPU
 * samples, per request, with matching {@code String}/{@code byte[]} allocation). This class builds
 * json-sKema's value tree straight from the Jackson tree instead.
 *
 * <p>Every produced node carries {@link UnknownSource#INSTANCE} as its {@code SourceLocation}.
 * Locations only enrich json-sKema's failure <em>messages</em> with line/column of the offending
 * input; they do not affect <em>whether</em> validation passes or fails. Programmatically-built
 * values legitimately have no source text, which is exactly what {@code UnknownSource} represents.
 * The trade-off is a validation failure that names the failing schema keyword and instance path
 * (still present) without a source line/column (never meaningful for a re-derived tree).
 *
 * <p>Number fidelity is preserved: Jackson's {@link JsonNode#numberValue()} returns an integral
 * {@link Number} (Integer/Long/BigInteger) for integral input and a decimal one (Double/BigDecimal)
 * otherwise, and json-sKema derives its {@code integer}-vs-{@code number} distinction from the
 * {@link Number} subtype — so {@code type: integer} constraints validate identically to the
 * text-parsed path.
 */
final class JacksonToSkema {

  private JacksonToSkema() {}

  static IJsonValue convert(JsonNode node) {
    if (node == null || node.isNull()) {
      return new JsonNull(UnknownSource.INSTANCE);
    }
    if (node.isObject()) {
      Map<JsonString, JsonValue> properties = new LinkedHashMap<>();
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        properties.put(
            new JsonString(entry.getKey(), UnknownSource.INSTANCE),
            (JsonValue) convert(entry.getValue()));
      }
      return new JsonObject(properties, UnknownSource.INSTANCE);
    }
    if (node.isArray()) {
      List<JsonValue> elements = new ArrayList<>();
      for (JsonNode element : node.values()) {
        elements.add((JsonValue) convert(element));
      }
      return new JsonArray(elements, UnknownSource.INSTANCE);
    }
    if (node.isString()) {
      return new JsonString(node.stringValue(), UnknownSource.INSTANCE);
    }
    if (node.isNumber()) {
      return new JsonNumber(node.numberValue(), UnknownSource.INSTANCE);
    }
    if (node.isBoolean()) {
      return new JsonBoolean(node.booleanValue(), UnknownSource.INSTANCE);
    }
    // Non-structural nodes — POJONode (from ObjectNode.putPOJO), BinaryNode, and any future
    // embedded type — are not reported by the isX() predicates above even though they have a
    // JSON serialization. Real request arguments arrive from the transport's JSON parse and are
    // never of these types, so this fallback is effectively unreachable on the hot path; but a
    // JsonNode built programmatically (e.g. via putPOJO) still must validate exactly as the old
    // node.toString()-then-parse path did. Serialize just this node and parse it, matching that
    // behavior faithfully rather than silently coercing it to null.
    return new JsonParser(node.toString()).parse();
  }
}
