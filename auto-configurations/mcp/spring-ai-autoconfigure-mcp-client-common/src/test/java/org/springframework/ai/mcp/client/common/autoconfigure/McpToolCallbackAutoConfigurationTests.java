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

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
	void disabledMcpToolCallbackAutoConfiguration() {
		// Test when MCP client is disabled
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean("mcpToolCallbacks");
			assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
		});

		// Test when toolcallback is disabled
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.toolcallback.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean("mcpToolCallbacks");
			assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
		});

		// Test when both are disabled
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.enabled=false", "spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean("mcpToolCallbacks");
				assertThat(context).doesNotHaveBean("mcpAsyncToolCallbacks");
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
	void customMcpToolFilterOverridesDefault() {
		// Test with SYNC mode
		this.applicationContext.withUserConfiguration(CustomToolFilterConfig.class).run(context -> {
			assertThat(context).hasBean("customToolFilter");
			McpToolFilter filter = context.getBean("customToolFilter", McpToolFilter.class);
			assertThat(filter).isInstanceOf(CustomToolFilter.class);
			assertThat(context).hasBean("mcpToolCallbacks");
			// Verify the custom filter is injected into the provider
			SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
			assertThat(provider).isNotNull();
		});

		// Test with ASYNC mode
		this.applicationContext.withUserConfiguration(CustomToolFilterConfig.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).hasBean("customToolFilter");
				McpToolFilter filter = context.getBean("customToolFilter", McpToolFilter.class);
				assertThat(filter).isInstanceOf(CustomToolFilter.class);
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
				// Verify the custom filter is injected into the provider
				AsyncMcpToolCallbackProvider provider = context.getBean(AsyncMcpToolCallbackProvider.class);
				assertThat(provider).isNotNull();
			});
	}

	@Test
	void customToolContextToMcpMetaConverterOverridesDefault() {
		// Test with SYNC mode
		this.applicationContext.withUserConfiguration(CustomConverterConfig.class).run(context -> {
			assertThat(context).hasBean("customConverter");
			ToolContextToMcpMetaConverter converter = context.getBean("customConverter",
					ToolContextToMcpMetaConverter.class);
			assertThat(converter).isInstanceOf(CustomToolContextToMcpMetaConverter.class);
			assertThat(context).hasBean("mcpToolCallbacks");
			// Verify the custom converter is injected into the provider
			SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
			assertThat(provider).isNotNull();
		});

		// Test with ASYNC mode
		this.applicationContext.withUserConfiguration(CustomConverterConfig.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).hasBean("customConverter");
				ToolContextToMcpMetaConverter converter = context.getBean("customConverter",
						ToolContextToMcpMetaConverter.class);
				assertThat(converter).isInstanceOf(CustomToolContextToMcpMetaConverter.class);
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
				// Verify the custom converter is injected into the provider
				AsyncMcpToolCallbackProvider provider = context.getBean(AsyncMcpToolCallbackProvider.class);
				assertThat(provider).isNotNull();
			});
	}

	@Test
	void providersCreatedWithMcpClients() {
		// Test SYNC mode with MCP clients
		this.applicationContext.withUserConfiguration(McpSyncClientConfig.class).run(context -> {
			assertThat(context).hasBean("mcpToolCallbacks");
			assertThat(context).hasBean("mcpSyncClient1");
			assertThat(context).hasBean("mcpSyncClient2");
			SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
			assertThat(provider).isNotNull();
		});

		// Test ASYNC mode with MCP clients
		this.applicationContext.withUserConfiguration(McpAsyncClientConfig.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC")
			.run(context -> {
				assertThat(context).hasBean("mcpAsyncToolCallbacks");
				assertThat(context).hasBean("mcpAsyncClient1");
				assertThat(context).hasBean("mcpAsyncClient2");
				AsyncMcpToolCallbackProvider provider = context.getBean(AsyncMcpToolCallbackProvider.class);
				assertThat(provider).isNotNull();
			});
	}

	@Test
	void providersCreatedWithoutMcpClients() {
		// Test SYNC mode without MCP clients
		this.applicationContext.run(context -> {
			assertThat(context).hasBean("mcpToolCallbacks");
			SyncMcpToolCallbackProvider provider = context.getBean(SyncMcpToolCallbackProvider.class);
			assertThat(provider).isNotNull();
		});

		// Test ASYNC mode without MCP clients
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.type=ASYNC").run(context -> {
			assertThat(context).hasBean("mcpAsyncToolCallbacks");
			AsyncMcpToolCallbackProvider provider = context.getBean(AsyncMcpToolCallbackProvider.class);
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

	@Configuration
	static class CustomToolFilterConfig {

		@Bean
		public McpToolFilter customToolFilter() {
			return new CustomToolFilter();
		}

	}

	static class CustomToolFilter implements McpToolFilter {

		@Override
		public boolean test(McpConnectionInfo metadata, McpSchema.Tool tool) {
			// Custom filter logic
			return !tool.name().startsWith("excluded_");
		}

	}

	@Configuration
	static class CustomConverterConfig {

		@Bean
		public ToolContextToMcpMetaConverter customConverter() {
			return new CustomToolContextToMcpMetaConverter();
		}

	}

	static class CustomToolContextToMcpMetaConverter implements ToolContextToMcpMetaConverter {

		@Override
		public Map<String, Object> convert(ToolContext toolContext) {
			// Custom conversion logic
			return Map.of("custom", "metadata");
		}

	}

	@Configuration
	static class McpSyncClientConfig {

		@Bean
		public List<McpSyncClient> mcpSyncClients() {
			return List.of(mcpSyncClient1(), mcpSyncClient2());
		}

		@Bean
		public McpSyncClient mcpSyncClient1() {
			return mock(McpSyncClient.class);
		}

		@Bean
		public McpSyncClient mcpSyncClient2() {
			return mock(McpSyncClient.class);
		}

	}

	@Configuration
	static class McpAsyncClientConfig {

		@Bean
		public List<McpAsyncClient> mcpAsyncClients() {
			return List.of(mcpAsyncClient1(), mcpAsyncClient2());
		}

		@Bean
		public McpAsyncClient mcpAsyncClient1() {
			return mock(McpAsyncClient.class);
		}

		@Bean
		public McpAsyncClient mcpAsyncClient2() {
			return mock(McpAsyncClient.class);
		}

	}

}
