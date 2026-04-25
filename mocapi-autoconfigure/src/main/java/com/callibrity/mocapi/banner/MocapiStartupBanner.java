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
package com.callibrity.mocapi.banner;

import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Logs a one-shot summary of the running mocapi configuration when the application becomes ready.
 * Prints registered handler counts, the active transport, the session-store backend, OAuth2 state,
 * and any observability modules — so ops teams can verify the runtime shape from a single line in
 * the logs at deploy time.
 *
 * <p>Discovery is bean-presence driven, with optional dependencies looked up by class name (rather
 * than imported) so this autoconfig has no hard classpath dependency on starters the user may not
 * have pulled in (OAuth2, observability, etc.). Each line gracefully collapses to a sensible
 * default when the backing module is absent.
 *
 * <p>Gated by {@code mocapi.banner.enabled} (default {@code true}); set it to {@code false} to
 * suppress.
 */
public class MocapiStartupBanner {

  private static final Logger log = LoggerFactory.getLogger(MocapiStartupBanner.class);

  // FIGlet "Standard" font, matched roughly to Spring Boot's banner weight. The "@" sits to the
  // left of the M so the wordmark reads "@Mocapi" visually. The trailing tagline line keeps
  // "@Mocapi" grep-able so ops can locate the banner in mixed log streams.
  private static final String ASCII_ART =
      """
         ____    __  __                       _\s
        / __ \\  |  \\/  | ___   ___ __ _ _ __ (_)
       / / _` | | |\\/| |/ _ \\ / __/ _` | '_ \\| |
      | | (_| | | |  | | (_) | (_| (_| | |_) | |
       \\ \\__,_| |_|  |_|\\___/ \\___\\__,_| .__/|_|
        \\____/                         |_|     \s
      """;

  private final ObjectProvider<McpToolsService> tools;
  private final ObjectProvider<McpPromptsService> prompts;
  private final ObjectProvider<McpResourcesService> resources;
  private final ObjectProvider<McpSessionStore> sessionStore;
  private final Environment env;
  private final ApplicationContext ctx;

  public MocapiStartupBanner(
      ObjectProvider<McpToolsService> tools,
      ObjectProvider<McpPromptsService> prompts,
      ObjectProvider<McpResourcesService> resources,
      ObjectProvider<McpSessionStore> sessionStore,
      Environment env,
      ApplicationContext ctx) {
    this.tools = tools;
    this.prompts = prompts;
    this.resources = resources;
    this.sessionStore = sessionStore;
    this.env = env;
    this.ctx = ctx;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    log.info("\n{}", render());
  }

  /** Builds the banner text. Package-private so tests can assert against the formatted output. */
  String render() {
    List<String> lines = new ArrayList<>();
    lines.add(ASCII_ART);
    lines.add(headline());
    lines.add("");
    lines.add(
        String.format(
            "  Tools: %d | Prompts: %d | Resources: %d",
            toolCount(), promptCount(), resourceCount()));
    lines.add("  Transport: " + describeTransport());
    lines.add("  Session store: " + describeSessionStore());
    lines.add("  OAuth2: " + describeOAuth2());
    String observability = describeObservability();
    if (!observability.isEmpty()) {
      lines.add("  Observability: " + observability);
    }
    return String.join("\n", lines);
  }

  private static String headline() {
    String version = MocapiStartupBanner.class.getPackage().getImplementationVersion();
    return version == null
        ? " :: @Mocapi :: ready"
        : " :: @Mocapi :: ready                  (v" + version + ")";
  }

  private int toolCount() {
    var svc = tools.getIfAvailable();
    return svc == null ? 0 : svc.allToolDescriptors().size();
  }

  private int promptCount() {
    var svc = prompts.getIfAvailable();
    return svc == null ? 0 : svc.allDescriptors().size();
  }

  private int resourceCount() {
    var svc = resources.getIfAvailable();
    if (svc == null) {
      return 0;
    }
    // Count fixed-URI and templated resources together — the LLM-side abstraction is "resources".
    return svc.allResourceHandlers().size() + svc.allResourceTemplateHandlers().size();
  }

  private String describeTransport() {
    boolean http = beanPresent("com.callibrity.mocapi.transport.http.StreamableHttpController");
    boolean stdio = beanPresent("com.callibrity.mocapi.transport.stdio.StdioServer");
    if (http) {
      String path = env.getProperty("mocapi.endpoint", "/mcp");
      return "Streamable HTTP at " + path;
    }
    if (stdio) {
      return "stdio";
    }
    return "(none detected)";
  }

  private String describeSessionStore() {
    var store = sessionStore.getIfAvailable();
    return store == null ? "(none)" : store.getClass().getSimpleName();
  }

  private String describeOAuth2() {
    // Detect OAuth2 by JwtDecoder bean presence. Looked up by class name so the banner doesn't
    // require the OAuth2 starter on the classpath. We don't enumerate audiences — keeping the
    // line succinct beats reflecting into Spring's properties machinery for one extra detail.
    return beanPresent("org.springframework.security.oauth2.jwt.JwtDecoder")
        ? "enabled (JWT)"
        : "disabled";
  }

  private String describeObservability() {
    List<String> modules = new ArrayList<>();
    if (beanPresent("com.callibrity.mocapi.audit.AuditLoggingInterceptor")) {
      modules.add("audit");
    }
    if (beanPresent("com.callibrity.mocapi.logging.McpMdcInterceptor")) {
      modules.add("mdc");
    }
    if (beanPresent("com.callibrity.mocapi.o11y.McpObservationFilter")) {
      modules.add("o11y");
    }
    return String.join(", ", modules);
  }

  private boolean beanPresent(String fqcn) {
    try {
      Class<?> type = Class.forName(fqcn, false, MocapiStartupBanner.class.getClassLoader());
      return ctx.getBeanNamesForType(type).length > 0;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
