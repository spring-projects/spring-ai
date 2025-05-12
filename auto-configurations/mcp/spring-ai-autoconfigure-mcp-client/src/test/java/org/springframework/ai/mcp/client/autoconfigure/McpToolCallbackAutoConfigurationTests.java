/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.client.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class McpToolCallbackAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpToolCallbackAutoConfiguration.class));

	@Test
	void enableddByDefault() {

		this.applicationContext.run(context -> {
			assertThat(context).hasBean("mcpToolCallbacks");
			assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
		});

		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.type=SYNC")
			.run(context -> {
				assertThat(context).hasBean("mcpToolCallbacks");
				assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
			});

		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).doesNotHaveBean("mcpToolCallbacks");
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
			});
	}

	@Test
	void enabledMcpToolCallbackAutoConfiguration() {

		// sync
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.toolcallback.enabled=true").run(context -> {
			assertThat(context).hasBean("mcpToolCallbacks");
			assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
		});

		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.toolcallback.enabled=true",
					"spring.ai.mcp.client.type=SYNC")
			.run(context -> {
				assertThat(context).hasBean("mcpToolCallbacks");
				assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
			});

		// Async
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.toolcallback.enabled=true", "spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).doesNotHaveBean("mcpToolCallbacks");
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
			});

		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.enabled=true", "spring.ai.mcp.client.toolcallback.enabled=true",
					"spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).doesNotHaveBean("mcpToolCallbacks");
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
			});
	}

}
