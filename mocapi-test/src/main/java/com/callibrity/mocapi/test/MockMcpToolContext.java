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
package com.callibrity.mocapi.test;

import com.callibrity.mocapi.api.sampling.CreateMessageRequestConfig;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link McpToolContext} implementation for unit-testing tool methods. Captures every progress
 * update, log entry, elicitation request, and sampling request made by the code under test, and
 * returns scripted (or canned) responses for {@code elicit} / {@code sample}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var ctx = new MockMcpToolContext();
 *
 * var result = ScopedValue.where(McpToolContext.CURRENT, ctx)
 *     .call(() -> myTool.invoke("arg"));
 *
 * assertThat(ctx.progressEvents()).hasSize(3);
 * assertThat(ctx.logEntries()).extracting(MockMcpToolContext.LogEntry::level)
 *     .contains(LoggingLevel.INFO);
 * assertThat(ctx.elicitCalls()).singleElement()
 *     .satisfies(call -> assertThat(call.params().message()).isEqualTo("Enter details"));
 * }</pre>
 *
 * <p>If no response is scripted, {@link #elicit} returns {@link ElicitAction#ACCEPT} with empty
 * content and {@link #sample} returns an assistant text message saying {@code "mock-response"}.
 * Tests that care about the response configure one via {@link #elicitResponse} / {@link
 * #sampleResponse}.
 *
 * <p>{@link #sample(Consumer)} resolves the customizer against a builder that captures the
 * configured request. Calling {@link CreateMessageRequestConfig#tool(String)}, {@link
 * CreateMessageRequestConfig#tools(String...)}, or {@link
 * CreateMessageRequestConfig#allServerTools()} throws {@link UnsupportedOperationException} — there
 * is no server tool registry available in unit tests. Stage a {@link
 * com.callibrity.mocapi.model.Tool} directly with {@link
 * CreateMessageRequestConfig#tool(com.callibrity.mocapi.model.Tool)} or script the response
 * instead.
 */
public final class MockMcpToolContext implements McpToolContext {

  /** Captured {@code sendProgress(progress, total)} invocation. */
  public record ProgressEvent(long progress, long total) {}

  /** Captured {@code log(level, logger, message)} invocation. */
  public record LogEntry(LoggingLevel level, String logger, String message) {}

  /** Captured {@code elicit} invocation. */
  public record ElicitCall(ElicitRequestFormParams params) {}

  /** Captured {@code sample} invocation — {@code params} is the built request. */
  public record SampleCall(CreateMessageRequestParams params) {}

  private final List<ProgressEvent> progressEvents = new ArrayList<>();
  private final List<LogEntry> logEntries = new ArrayList<>();
  private final List<ElicitCall> elicitCalls = new ArrayList<>();
  private final List<SampleCall> sampleCalls = new ArrayList<>();

  private Function<ElicitRequestFormParams, ElicitResult> elicitResponder =
      params -> defaultElicit();
  private Function<CreateMessageRequestParams, CreateMessageResult> sampleResponder =
      params -> defaultSample();

  @Override
  public void sendProgress(long progress, long total) {
    progressEvents.add(new ProgressEvent(progress, total));
  }

  @Override
  public void log(LoggingLevel level, String logger, String message) {
    logEntries.add(new LogEntry(level, logger, message));
  }

  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    elicitCalls.add(new ElicitCall(params));
    return elicitResponder.apply(params);
  }

  @Override
  public CreateMessageResult sample(CreateMessageRequestParams params) {
    sampleCalls.add(new SampleCall(params));
    return sampleResponder.apply(params);
  }

  @Override
  public CreateMessageResult sample(Consumer<CreateMessageRequestConfig> customizer) {
    var config = new TestCreateMessageRequestConfig();
    customizer.accept(config);
    return sample(config.build());
  }

  // --- Captured calls --------------------------------------------------------------------------

  /** Defensive copy of every {@code sendProgress} invocation in order. */
  public List<ProgressEvent> progressEvents() {
    return List.copyOf(progressEvents);
  }

  /** Defensive copy of every {@code log} invocation in order. */
  public List<LogEntry> logEntries() {
    return List.copyOf(logEntries);
  }

  /** Defensive copy of every {@code elicit} invocation in order. */
  public List<ElicitCall> elicitCalls() {
    return List.copyOf(elicitCalls);
  }

  /** Defensive copy of every {@code sample} invocation in order. */
  public List<SampleCall> sampleCalls() {
    return List.copyOf(sampleCalls);
  }

  // --- Scripted responses ----------------------------------------------------------------------

  /** Return the given fixed {@link ElicitResult} for every subsequent {@code elicit} call. */
  public MockMcpToolContext elicitResponse(ElicitResult fixed) {
    this.elicitResponder = params -> fixed;
    return this;
  }

  /** Compute the {@link ElicitResult} per-call from the request parameters. */
  public MockMcpToolContext elicitResponse(Function<ElicitRequestFormParams, ElicitResult> fn) {
    this.elicitResponder = fn;
    return this;
  }

  /**
   * Return the given fixed {@link CreateMessageResult} for every subsequent {@code sample} call.
   */
  public MockMcpToolContext sampleResponse(CreateMessageResult fixed) {
    this.sampleResponder = params -> fixed;
    return this;
  }

  /** Compute the {@link CreateMessageResult} per-call from the built request parameters. */
  public MockMcpToolContext sampleResponse(
      Function<CreateMessageRequestParams, CreateMessageResult> fn) {
    this.sampleResponder = fn;
    return this;
  }

  /** Clear all captured calls and restore default responses. */
  public void reset() {
    progressEvents.clear();
    logEntries.clear();
    elicitCalls.clear();
    sampleCalls.clear();
    elicitResponder = params -> defaultElicit();
    sampleResponder = params -> defaultSample();
  }

  private static ElicitResult defaultElicit() {
    return new ElicitResult(ElicitAction.ACCEPT, null);
  }

  private static CreateMessageResult defaultSample() {
    return new CreateMessageResult(
        Role.ASSISTANT, new TextContent("mock-response", null), null, null);
  }
}
