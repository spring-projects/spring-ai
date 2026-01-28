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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link StatelessToolCallbackConverterAutoConfiguration} and
 * {@link StatelessToolCallbackConverterAutoConfiguration.ToolCallbackConverterCondition}.
 *
 * @author Christian Tzolov
 */
public class StatelessToolCallbackConverterAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(StatelessToolCallbackConverterAutoConfiguration.class))
		.withPropertyValues("spring.ai.mcp.server.enabled=true", "spring.ai.mcp.server.protocol=STATELESS");

	@Test
	void defaultSyncToolsConfiguration() {
		this.contextRunner.withUserConfiguration(TestToolConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasBean("syncTools");

			@SuppressWarnings("unchecked")
			List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");
			assertThat(syncTools).hasSize(1);
			assertThat(syncTools.get(0)).isNotNull();
		});
	}

	@Test
	void asyncToolsConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
				assertThat(context).hasBean("asyncTools");
				assertThat(context).doesNotHaveBean("syncTools");

				@SuppressWarnings("unchecked")
				List<AsyncToolSpecification> asyncTools = (List<AsyncToolSpecification>) context.getBean("asyncTools");
				assertThat(asyncTools).hasSize(1);
				assertThat(asyncTools.get(0)).isNotNull();
			});
	}

	@Test
	void toolCallbackProviderConfiguration() {
		this.contextRunner.withUserConfiguration(TestToolCallbackProviderConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasBean("syncTools");

			@SuppressWarnings("unchecked")
			List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");
			assertThat(syncTools).hasSize(1);
		});
	}

	@Test
	void multipleToolCallbacksConfiguration() {
		this.contextRunner.withUserConfiguration(TestMultipleToolsConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasBean("syncTools");

			@SuppressWarnings("unchecked")
			List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");
			assertThat(syncTools).hasSize(2);
		});
	}

	@Test
	void toolResponseMimeTypeConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.tool-response-mime-type.test-tool=application/json")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
				assertThat(context).hasBean("syncTools");

				@SuppressWarnings("unchecked")
				List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");
				assertThat(syncTools).hasSize(1);

				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getToolResponseMimeType()).containsEntry("test-tool", "application/json");
			});
	}

	@Test
	void duplicateToolNamesDeduplication() {
		this.contextRunner.withUserConfiguration(TestDuplicateToolsConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasBean("syncTools");

			@SuppressWarnings("unchecked")
			List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");

			// On duplicate key, keep the existing tool
			assertThat(syncTools).hasSize(1);
		});
	}

	@Test
	void conditionDisabledWhenServerDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				assertThat(context).doesNotHaveBean(StatelessToolCallbackConverterAutoConfiguration.class);
				assertThat(context).doesNotHaveBean("syncTools");
				assertThat(context).doesNotHaveBean("asyncTools");
			});
	}

	@Test
	void conditionDisabledWhenToolCallbackConvertDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.tool-callback-converter=false")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				assertThat(context).doesNotHaveBean(StatelessToolCallbackConverterAutoConfiguration.class);
				assertThat(context).doesNotHaveBean("syncTools");
				assertThat(context).doesNotHaveBean("asyncTools");
			});
	}

	@Test
	void conditionEnabledByDefault() {
		this.contextRunner.withUserConfiguration(TestToolConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasBean("syncTools");
		});
	}

	@Test
	void conditionEnabledExplicitly() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.enabled=true",
					"spring.ai.mcp.server.tool-callback-converter=true")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
				assertThat(context).hasBean("syncTools");
			});
	}

	@Test
	void emptyToolCallbacksConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
			assertThat(context).hasBean("syncTools");

			@SuppressWarnings("unchecked")
			List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");
			assertThat(syncTools).isEmpty();
		});
	}

	@Test
	void mixedToolCallbacksAndProvidersConfiguration() {
		this.contextRunner
			.withUserConfiguration(TestToolConfiguration.class, TestToolCallbackProviderConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(StatelessToolCallbackConverterAutoConfiguration.class);
				assertThat(context).hasBean("syncTools");

				@SuppressWarnings("unchecked")
				List<SyncToolSpecification> syncTools = (List<SyncToolSpecification>) context.getBean("syncTools");
				assertThat(syncTools).hasSize(2); // One from direct callback, one from
													// provider
			});
	}

	@Configuration
	static class TestToolConfiguration {

		@Bean
		List<ToolCallback> testToolCallbacks() {
			McpSyncClient mockClient = Mockito.mock(McpSyncClient.class);
			McpSchema.Tool mockTool = Mockito.mock(McpSchema.Tool.class);
			McpSchema.CallToolResult mockResult = Mockito.mock(McpSchema.CallToolResult.class);

			Mockito.when(mockTool.name()).thenReturn("test-tool");
			Mockito.when(mockTool.description()).thenReturn("Test Tool");
			Mockito.when(mockClient.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult);
			when(mockClient.getClientInfo()).thenReturn(new McpSchema.Implementation("testClient", "1.0.0"));

			return List.of(SyncMcpToolCallback.builder().mcpClient(mockClient).tool(mockTool).build());
		}

	}

	@Configuration
	static class TestMultipleToolsConfiguration {

		@Bean
		List<ToolCallback> testMultipleToolCallbacks() {
			McpSyncClient mockClient1 = Mockito.mock(McpSyncClient.class);
			McpSchema.Tool mockTool1 = Mockito.mock(McpSchema.Tool.class);
			McpSchema.CallToolResult mockResult1 = Mockito.mock(McpSchema.CallToolResult.class);

			Mockito.when(mockTool1.name()).thenReturn("test-tool-1");
			Mockito.when(mockTool1.description()).thenReturn("Test Tool 1");
			Mockito.when(mockClient1.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult1);
			when(mockClient1.getClientInfo()).thenReturn(new McpSchema.Implementation("testClient1", "1.0.0"));

			McpSyncClient mockClient2 = Mockito.mock(McpSyncClient.class);
			McpSchema.Tool mockTool2 = Mockito.mock(McpSchema.Tool.class);
			McpSchema.CallToolResult mockResult2 = Mockito.mock(McpSchema.CallToolResult.class);

			Mockito.when(mockTool2.name()).thenReturn("test-tool-2");
			Mockito.when(mockTool2.description()).thenReturn("Test Tool 2");
			Mockito.when(mockClient2.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult2);
			when(mockClient2.getClientInfo()).thenReturn(new McpSchema.Implementation("testClient2", "1.0.0"));

			return List.of(SyncMcpToolCallback.builder().mcpClient(mockClient1).tool(mockTool1).build(),
					SyncMcpToolCallback.builder().mcpClient(mockClient2).tool(mockTool2).build());
		}

	}

	@Configuration
	static class TestDuplicateToolsConfiguration {

		@Bean
		List<ToolCallback> testDuplicateToolCallbacks() {
			McpSyncClient mockClient1 = Mockito.mock(McpSyncClient.class);
			McpSchema.Tool mockTool1 = Mockito.mock(McpSchema.Tool.class);
			McpSchema.CallToolResult mockResult1 = Mockito.mock(McpSchema.CallToolResult.class);

			Mockito.when(mockTool1.name()).thenReturn("duplicate-tool");
			Mockito.when(mockTool1.description()).thenReturn("First Tool");
			Mockito.when(mockClient1.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult1);
			when(mockClient1.getClientInfo()).thenReturn(new McpSchema.Implementation("frist_client", "1.0.0"));

			McpSyncClient mockClient2 = Mockito.mock(McpSyncClient.class);
			McpSchema.Tool mockTool2 = Mockito.mock(McpSchema.Tool.class);
			McpSchema.CallToolResult mockResult2 = Mockito.mock(McpSchema.CallToolResult.class);

			Mockito.when(mockTool2.name()).thenReturn("duplicate-tool");
			Mockito.when(mockTool2.description()).thenReturn("Second Tool");
			Mockito.when(mockClient2.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult2);
			when(mockClient2.getClientInfo()).thenReturn(new McpSchema.Implementation("second_client", "1.0.0"));

			return List.of(SyncMcpToolCallback.builder().mcpClient(mockClient1).tool(mockTool1).build(),
					SyncMcpToolCallback.builder().mcpClient(mockClient2).tool(mockTool2).build());
		}

	}

	@Configuration
	static class TestToolCallbackProviderConfiguration {

		@Bean
		ToolCallbackProvider testToolCallbackProvider() {
			return () -> {
				McpSyncClient mockClient = Mockito.mock(McpSyncClient.class);
				McpSchema.Tool mockTool = Mockito.mock(McpSchema.Tool.class);
				McpSchema.CallToolResult mockResult = Mockito.mock(McpSchema.CallToolResult.class);

				Mockito.when(mockTool.name()).thenReturn("provider-tool");
				Mockito.when(mockTool.description()).thenReturn("Provider Tool");
				Mockito.when(mockClient.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult);
				when(mockClient.getClientInfo()).thenReturn(new McpSchema.Implementation("testClient", "1.0.0"));

				return new ToolCallback[] {
						SyncMcpToolCallback.builder().mcpClient(mockClient).tool(mockTool).build() };
			};
		}

	}

}
