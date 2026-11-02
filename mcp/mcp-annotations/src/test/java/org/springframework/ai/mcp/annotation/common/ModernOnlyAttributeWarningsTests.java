/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.annotation.common;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.annotation.McpClientCapability;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.resource.SyncMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncModernMcpToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The warnings for annotation attributes that only the 2026-07-28 ("modern") MCP server
 * honours: {@code requiredCapabilities} and the resource cache hints on a legacy server,
 * and {@code requiredCapabilities} on a modern method that cannot ask for input.
 *
 * @author Christian Tzolov
 */
class ModernOnlyAttributeWarningsTests {

	private final ListAppender<ILoggingEvent> requiredCapabilities = new ListAppender<>();

	private final ListAppender<ILoggingEvent> cacheHints = new ListAppender<>();

	private final ListAppender<ILoggingEvent> modernSupport = new ListAppender<>();

	@BeforeEach
	void captureWarnings() {
		attach(McpRequiredCapabilities.class, this.requiredCapabilities);
		attach(McpResourceCacheHints.class, this.cacheHints);
		attach(ModernMethodSupport.class, this.modernSupport);
	}

	@AfterEach
	void detach() {
		logger(McpRequiredCapabilities.class).detachAppender(this.requiredCapabilities);
		logger(McpResourceCacheHints.class).detachAppender(this.cacheHints);
		logger(ModernMethodSupport.class).detachAppender(this.modernSupport);
	}

	@Test
	void legacyToolProviderWarnsAboutRequiredCapabilities() {
		assertThat(new SyncMcpToolProvider(List.of(new Gated())).getToolSpecifications()).hasSize(2);

		assertThat(warnings(this.requiredCapabilities)).singleElement()
			.satisfies(message -> assertThat(message).startsWith("@McpTool requiredCapabilities [SAMPLING] on ")
				.contains(Gated.class.getName() + ".gated")
				.contains("spring.ai.mcp.server.protocol=MODERN"));
	}

	@Test
	void legacyPromptProviderWarnsAboutRequiredCapabilities() {
		assertThat(new SyncMcpPromptProvider(List.of(new Gated())).getPromptSpecifications()).hasSize(1);

		assertThat(warnings(this.requiredCapabilities)).singleElement()
			.satisfies(message -> assertThat(message).startsWith("@McpPrompt requiredCapabilities [ELICITATION_FORM]")
				.contains(".prompt"));
	}

	@Test
	void legacyResourceProviderWarnsAboutRequiredCapabilitiesAndCacheHints() {
		assertThat(new SyncMcpResourceProvider(List.of(new Gated())).getResourceSpecifications()).hasSize(2);

		assertThat(warnings(this.requiredCapabilities)).singleElement()
			.satisfies(message -> assertThat(message).startsWith("@McpResource requiredCapabilities [ROOTS]")
				.contains(".read"));
		assertThat(warnings(this.cacheHints)).singleElement()
			.satisfies(message -> assertThat(message).startsWith("@McpResource ttlMs/cacheScope on ")
				.contains(".cached")
				.contains("spring.ai.mcp.server.protocol=MODERN"));
	}

	@Test
	void defaultsProduceNoWarning() {
		new SyncMcpToolProvider(List.of(new Gated())).getToolSpecifications();
		new SyncMcpPromptProvider(List.of(new Gated())).getPromptSpecifications();
		new SyncMcpResourceProvider(List.of(new Gated())).getResourceSpecifications();

		// One warning per annotated method, none for the methods with default attributes.
		assertThat(warnings(this.requiredCapabilities)).hasSize(3);
		assertThat(warnings(this.cacheHints)).hasSize(1);
	}

	@Test
	void modernProviderWarnsWhenTheReturnTypeCannotAskForInput() {
		List<McpSyncTool> tools = new SyncModernMcpToolProvider(List.of(new Gated())).getTools();

		// The capabilities are still declared, so the server refuses the call up front.
		assertThat(tools).extracting(tool -> tool.definition().name()).containsExactly("gated", "plain");
		assertThat(tools.get(0).requiredCapabilities().supportsSampling()).isTrue();
		assertThat(tools.get(1).requiredCapabilities()).isEqualTo(ClientCapabilities.NONE);
		assertThat(warnings(this.modernSupport)).singleElement()
			.satisfies(message -> assertThat(message).startsWith("Method gated in " + Gated.class.getName())
				.contains("requires [SAMPLING] but returns java.lang.String")
				.contains("InputRequiredResult"));
		assertThat(warnings(this.requiredCapabilities)).isEmpty();
	}

	@Test
	void modernProviderDoesNotWarnForAnOutcomeReturnType() {
		new SyncModernMcpToolProvider(List.of(new Outcome())).getTools();

		assertThat(warnings(this.modernSupport)).isEmpty();
	}

	private static void attach(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
		appender.start();
		logger(loggerClass).addAppender(appender);
	}

	private static Logger logger(Class<?> loggerClass) {
		return (Logger) LoggerFactory.getLogger(loggerClass);
	}

	private static List<String> warnings(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream()
			.filter(event -> event.getLevel() == Level.WARN)
			.map(ILoggingEvent::getFormattedMessage)
			.toList();
	}

	@SuppressWarnings("unused")
	static class Gated {

		@McpTool(name = "gated", description = "gated", requiredCapabilities = McpClientCapability.SAMPLING)
		String gated() {
			return "x";
		}

		@McpTool(name = "plain", description = "plain")
		String plain() {
			return "x";
		}

		@McpPrompt(name = "prompt", description = "prompt", requiredCapabilities = McpClientCapability.ELICITATION_FORM)
		String prompt() {
			return "x";
		}

		@McpResource(uri = "test://read", name = "read", description = "read",
				requiredCapabilities = McpClientCapability.ROOTS)
		String read() {
			return "x";
		}

		@McpResource(uri = "test://cached", name = "cached", description = "cached", ttlMs = 1000,
				cacheScope = CacheScope.PUBLIC)
		String cached() {
			return "x";
		}

	}

	@SuppressWarnings("unused")
	static class Outcome {

		@McpTool(name = "outcome", description = "outcome", requiredCapabilities = McpClientCapability.SAMPLING)
		CallToolOutcome outcome() {
			return CallToolResult.builder().addContent(TextContent.builder("x").build()).build();
		}

	}

}
