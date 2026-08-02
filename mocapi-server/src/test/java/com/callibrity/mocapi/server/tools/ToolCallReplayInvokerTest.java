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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.mocapi.server.progress.DefaultMcpProgressSource;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.ThrowingTool;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolCallReplayInvokerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);
  private final MrtrElicitationEngine elicitationEngine =
      new MrtrElicitationEngine(
          RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, new ObjectMapper()),
          new ObjectMapper());

  private McpToolsService service;

  private List<CallToolHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpTool.class).stream()
        .map(
            m ->
                CallToolHandlers.build(
                    target,
                    m,
                    new CallToolHandlers.BuildParams(
                        generator, mapper, List.of(), List.of(), s -> s, false)))
        .toList();
  }

  @BeforeEach
  void setUp() {
    var handlers = new ArrayList<CallToolHandler>();
    handlers.addAll(createHandlers(new EchoTool()));
    handlers.addAll(createHandlers(new ThrowingTool()));
    service = new McpToolsService(List.copyOf(handlers), mapper, elicitationEngine);
  }

  private McpExchange formCapableExchange() {
    return new McpExchange(
        "2026-07-28",
        null,
        new ClientCapabilities(
            null,
            null,
            null,
            new ElicitationCapability(JsonNodeFactory.instance.objectNode(), null),
            null));
  }

  @Test
  void detached_invoke_with_empty_ledger_yields_input_required() {
    var outcome =
        service.invoke(
            "echo-tool.confirm-and-echo",
            JsonNodeFactory.instance.objectNode(),
            List.of(),
            new DefaultMcpProgressSource((p, t, m) -> {}),
            formCapableExchange());

    assertThat(outcome).isInstanceOf(ToolCallReplayInvoker.Outcome.InputRequired.class);
    var ir = (ToolCallReplayInvoker.Outcome.InputRequired) outcome;
    assertThat(ir.key()).isEqualTo("elicit-1");
    assertThat(ir.ledger()).hasSize(1);
  }

  @Test
  void detached_invoke_with_answered_ledger_completes() {
    var first =
        (ToolCallReplayInvoker.Outcome.InputRequired)
            service.invoke(
                "echo-tool.confirm-and-echo",
                JsonNodeFactory.instance.objectNode(),
                List.of(),
                new DefaultMcpProgressSource((p, t, m) -> {}),
                formCapableExchange());

    var content = JsonNodeFactory.instance.objectNode();
    content.put("ok", true);
    var answered =
        List.of(
            first.ledger().getFirst().answeredWith(new ElicitResult(ElicitAction.ACCEPT, content)));

    var outcome =
        service.invoke(
            "echo-tool.confirm-and-echo",
            JsonNodeFactory.instance.objectNode(),
            answered,
            new DefaultMcpProgressSource((p, t, m) -> {}),
            formCapableExchange());

    assertThat(outcome).isInstanceOf(ToolCallReplayInvoker.Outcome.Completed.class);
    var result = ((ToolCallReplayInvoker.Outcome.Completed) outcome).result();
    assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("confirmed");
  }

  @Test
  void tool_exception_maps_to_isError_result_not_a_throw() {
    var outcome =
        service.invoke(
            "throwing-tool.explode",
            mapper.createObjectNode().put("input", "test"),
            List.of(),
            new DefaultMcpProgressSource((p, t, m) -> {}),
            formCapableExchange());

    var completed = (ToolCallReplayInvoker.Outcome.Completed) outcome;
    assertThat(completed.result().isError()).isTrue();
  }
}

@Component
class EchoTool {
  @McpTool(description = "interactive echo")
  public String confirmAndEcho(McpToolContext ctx) {
    ElicitResult answer = ctx.elicit("Proceed?", schema -> schema.bool("ok", "OK?"));
    return answer.isAccepted() ? "confirmed" : "declined";
  }
}
