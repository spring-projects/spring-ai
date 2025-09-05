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

package org.springframework.ai.mcp.client.common.autoconfigure;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class McpToolCallbackAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpToolCallbackAutoConfiguration.class));

	@Test
	void enabledByDefault() {

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

	@Test
	void defaultMcpToolNamePrefixGeneratorIsCreated() {
		// Test with SYNC mode (default)
		this.applicationContext.run(context -> {
			assertThat(context).hasBean("mcpToolNamePrefixGenerator");
			McpToolNamePrefixGenerator generator = context.getBean(McpToolNamePrefixGenerator.class);
			assertThat(generator).isNotNull();
		});

		// Test with ASYNC mode
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.type=ASYNC").run(context -> {
			assertThat(context).hasBean("mcpToolNamePrefixGenerator");
			McpToolNamePrefixGenerator generator = context.getBean(McpToolNamePrefixGenerator.class);
			assertThat(generator).isNotNull();
		});
	}

	@Test
	void customMcpToolNamePrefixGeneratorOverridesDefault() {
		// Test with SYNC mode
		this.applicationContext.withUserConfiguration(CustomPrefixGeneratorConfig.class).run(context -> {
			assertThat(context).hasBean("mcpToolNamePrefixGenerator");
			McpToolNamePrefixGenerator generator = context.getBean(McpToolNamePrefixGenerator.class);
			assertThat(generator).isInstanceOf(CustomPrefixGenerator.class);
			assertThat(context).hasBean("mcpToolCallbacks");
			// Verify the custom generator is injected into the provider
			SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
			assertThat(provider).isNotNull();
		});

		// Test with ASYNC mode
		this.applicationContext.withUserConfiguration(CustomPrefixGeneratorConfig.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).hasBean("mcpToolNamePrefixGenerator");
				McpToolNamePrefixGenerator generator = context.getBean(McpToolNamePrefixGenerator.class);
				assertThat(generator).isInstanceOf(CustomPrefixGenerator.class);
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
				// Verify the custom generator is injected into the provider
				AsyncMcpToolCallbackProvider provider = context.getBean(AsyncMcpToolCallbackProvider.class);
				assertThat(provider).isNotNull();
			});
	}

	@Test
	void mcpToolNamePrefixGeneratorIsInjectedIntoProviders() {
		// Test SYNC provider receives the generator
		this.applicationContext.run(context -> {
			assertThat(context).hasBean("mcpToolNamePrefixGenerator");
			assertThat(context).hasBean("mcpToolCallbacks");

			McpToolNamePrefixGenerator generator = context.getBean(McpToolNamePrefixGenerator.class);
			SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);

			assertThat(generator).isNotNull();
			assertThat(provider).isNotNull();
		});

		// Test ASYNC provider receives the generator
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.type=ASYNC").run(context -> {
			assertThat(context).hasBean("mcpToolNamePrefixGenerator");
			assertThat(context).hasBean("mcpAsyncToolCallbacks");

			McpToolNamePrefixGenerator generator = context.getBean(McpToolNamePrefixGenerator.class);
			AsyncMcpToolCallbackProvider provider = context.getBean(AsyncMcpToolCallbackProvider.class);

			assertThat(generator).isNotNull();
			assertThat(provider).isNotNull();
		});
	}

	@Configuration
	static class CustomPrefixGeneratorConfig {

		@Bean
		public McpToolNamePrefixGenerator mcpToolNamePrefixGenerator() {
			return new CustomPrefixGenerator();
		}

	}

	static class CustomPrefixGenerator implements McpToolNamePrefixGenerator {

		@Override
		public String prefixedToolName(McpConnectionInfo mcpConnInfo, Tool tool) {
			return "custom_" + tool.name();
		}

	}

}
