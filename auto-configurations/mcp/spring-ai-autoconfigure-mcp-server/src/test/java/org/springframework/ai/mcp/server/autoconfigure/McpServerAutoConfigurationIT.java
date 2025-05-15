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

package org.springframework.ai.mcp.server.autoconfigure;

import java.util.List;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.type.TypeReference;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.mockito.Mockito.when;

public class McpServerAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class));

	String emptyJsonSchema = """
			{
				"$schema": "http://json-schema.org/draft-07/schema#",
				"type": "object",
				"properties": {}
			}
			""";

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(McpSyncServer.class);
			assertThat(context).hasSingleBean(McpServerTransportProvider.class);
			assertThat(context.getBean(McpServerTransportProvider.class))
				.isInstanceOf(StdioServerTransportProvider.class);

			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getName()).isEqualTo("mcp-server");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.getType()).isEqualTo(McpServerProperties.ServerType.SYNC);
			assertThat(properties.isToolChangeNotification()).isTrue();
			assertThat(properties.isResourceChangeNotification()).isTrue();
			assertThat(properties.isPromptChangeNotification()).isTrue();
		});
	}

	@Test
	void asyncConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.name=test-server",
					"spring.ai.mcp.server.version=2.0.0", "spring.ai.mcp.server.instructions=My MCP Server")
			.run(context -> {
				assertThat(context).hasSingleBean(McpAsyncServer.class);
				assertThat(context).doesNotHaveBean(McpSyncServer.class);

				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getName()).isEqualTo("test-server");
				assertThat(properties.getVersion()).isEqualTo("2.0.0");
				assertThat(properties.getInstructions()).isEqualTo("My MCP Server");
				assertThat(properties.getType()).isEqualTo(McpServerProperties.ServerType.ASYNC);
			});
	}

	@Test
	void transportConfiguration() {
		this.contextRunner.withUserConfiguration(CustomTransportConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(McpServerTransport.class);
			assertThat(context.getBean(McpServerTransport.class)).isInstanceOf(CustomServerTransport.class);
		});
	}

	@Test
	void serverNotificationConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.tool-change-notification=false",
					"spring.ai.mcp.server.resource-change-notification=false")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.isToolChangeNotification()).isFalse();
				assertThat(properties.isResourceChangeNotification()).isFalse();
			});
	}

	// @Test
	void invalidConfigurationThrowsException() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.version=invalid-version").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context).getFailure()
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid version format");
		});
	}

	@Test
	void disabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(McpSyncServer.class);
			assertThat(context).doesNotHaveBean(McpAsyncServer.class);
			assertThat(context).doesNotHaveBean(McpServerTransport.class);
		});
	}

	@Test
	void asyncNotificationDefaultConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.isToolChangeNotification()).isTrue();
			assertThat(properties.isResourceChangeNotification()).isTrue();
			assertThat(properties.isPromptChangeNotification()).isTrue();

			McpAsyncServer mcpAsyncServer = context.getBean(McpAsyncServer.class);

			McpSchema.Tool newTool = new McpSchema.Tool("new-tool", "New test tool", emptyJsonSchema);
			StepVerifier
				.create(mcpAsyncServer.addTool(new McpServerFeatures.AsyncToolSpecification(newTool,
						(exchange, args) -> Mono.just(new McpSchema.CallToolResult(List.of(), false)))))
				.verifyComplete();

			McpSchema.Resource resource = new McpSchema.Resource("test://resource", "Test Resource",
					"Test resource description", "text/plain", null);
			StepVerifier.create(mcpAsyncServer.addResource(new McpServerFeatures.AsyncResourceSpecification(resource,
					(exchange, req) -> Mono.just(new McpSchema.ReadResourceResult(List.of())))))
				.verifyComplete();

			McpSchema.Prompt prompt = new McpSchema.Prompt("test-prompt", "Test Prompt", List.of());

			StepVerifier
				.create(mcpAsyncServer.addPrompt(
						new McpServerFeatures.AsyncPromptSpecification(prompt,
								(exchange,
										req) -> Mono.just(new McpSchema.GetPromptResult("Test prompt description",
												List.of(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT,
														new McpSchema.TextContent("Test content"))))))))
				.verifyComplete();

		});
	}

	@Test
	void syncNotificationDefaultConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=SYNC").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.isToolChangeNotification()).isTrue();
			assertThat(properties.isResourceChangeNotification()).isTrue();
			assertThat(properties.isPromptChangeNotification()).isTrue();

			McpSyncServer mcpSyncServer = context.getBean(McpSyncServer.class);

			McpSchema.Tool newTool = new McpSchema.Tool("new-tool", "New test tool", emptyJsonSchema);
			assertThatNoException()
				.isThrownBy(() -> mcpSyncServer.addTool(new McpServerFeatures.SyncToolSpecification(newTool,
						(exchange, args) -> new McpSchema.CallToolResult(List.of(), false))));

			McpSchema.Resource resource = new McpSchema.Resource("test://resource", "Test Resource",
					"Test resource description", "text/plain", null);
			assertThatNoException()
				.isThrownBy(() -> mcpSyncServer.addResource(new McpServerFeatures.SyncResourceSpecification(resource,
						(exchange, req) -> new McpSchema.ReadResourceResult(List.of()))));

			McpSchema.Prompt prompt = new McpSchema.Prompt("test-prompt", "Test Prompt", List.of());
			assertThatNoException()
				.isThrownBy(() -> mcpSyncServer.addPrompt(new McpServerFeatures.SyncPromptSpecification(prompt,
						(exchange, req) -> new McpSchema.GetPromptResult("Test prompt description",
								List.of(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT,
										new McpSchema.TextContent("Test content")))))));
		});
	}

	@Test
	void notificationConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.tool-change-notification=false",
					"spring.ai.mcp.server.resource-change-notification=false",
					"spring.ai.mcp.server.prompt-change-notification=false")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.isToolChangeNotification()).isFalse();
				assertThat(properties.isResourceChangeNotification()).isFalse();
				assertThat(properties.isPromptChangeNotification()).isFalse();
			});
	}

	@Test
	void stdioConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.isStdio()).isTrue();
		});
	}

	@Test
	void serverCapabilitiesConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(McpSchema.ServerCapabilities.Builder.class);
			McpSchema.ServerCapabilities.Builder builder = context.getBean(McpSchema.ServerCapabilities.Builder.class);
			assertThat(builder).isNotNull();
		});
	}

	@Test
	void toolSpecificationConfiguration() {
		this.contextRunner.withUserConfiguration(TestToolConfiguration.class).run(context -> {
			List<SyncToolSpecification> tools = context.getBean("syncTools", List.class);
			assertThat(tools).hasSize(1);
		});
	}

	@Test
	void resourceSpecificationConfiguration() {
		this.contextRunner.withUserConfiguration(TestResourceConfiguration.class).run(context -> {
			McpSyncServer server = context.getBean(McpSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Test
	void promptSpecificationConfiguration() {
		this.contextRunner.withUserConfiguration(TestPromptConfiguration.class).run(context -> {
			McpSyncServer server = context.getBean(McpSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Test
	void asyncToolSpecificationConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				List<AsyncToolSpecification> tools = context.getBean("asyncTools", List.class);
				assertThat(tools).hasSize(1);
			});
	}

	@Test
	void customCapabilitiesBuilder() {
		this.contextRunner.withUserConfiguration(CustomCapabilitiesConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(McpSchema.ServerCapabilities.Builder.class);
			assertThat(context.getBean(McpSchema.ServerCapabilities.Builder.class))
				.isInstanceOf(CustomCapabilitiesBuilder.class);
		});
	}

	@Test
	void rootsChangeHandlerConfiguration() {
		this.contextRunner.withUserConfiguration(TestRootsHandlerConfiguration.class).run(context -> {
			McpSyncServer server = context.getBean(McpSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Configuration
	static class TestResourceConfiguration {

		@Bean
		List<SyncResourceSpecification> testResources() {
			return List.of();
		}

	}

	@Configuration
	static class TestPromptConfiguration {

		@Bean
		List<SyncPromptSpecification> testPrompts() {
			return List.of();
		}

	}

	@Configuration
	static class CustomCapabilitiesConfiguration {

		@Bean
		McpSchema.ServerCapabilities.Builder customCapabilitiesBuilder() {
			return new CustomCapabilitiesBuilder();
		}

	}

	static class CustomCapabilitiesBuilder extends McpSchema.ServerCapabilities.Builder {

		// Custom implementation for testing

	}

	@Configuration
	static class TestToolConfiguration {

		@Bean
		List<ToolCallback> testTool() {
			McpSyncClient mockClient = Mockito.mock(McpSyncClient.class);
			McpSchema.Tool mockTool = Mockito.mock(McpSchema.Tool.class);
			McpSchema.CallToolResult mockResult = Mockito.mock(McpSchema.CallToolResult.class);

			Mockito.when(mockTool.name()).thenReturn("test-tool");
			Mockito.when(mockTool.description()).thenReturn("Test Tool");
			Mockito.when(mockClient.callTool(Mockito.any(McpSchema.CallToolRequest.class))).thenReturn(mockResult);
			when(mockClient.getClientInfo()).thenReturn(new McpSchema.Implementation("testClient", "1.0.0"));

			return List.of(new SyncMcpToolCallback(mockClient, mockTool));
		}

	}

	@Configuration
	static class TestRootsHandlerConfiguration {

		@Bean
		BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootsChangeHandler() {
			return (exchange, roots) -> {
				// Test implementation
			};
		}

	}

	static class CustomServerTransport implements McpServerTransport {

		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			return Mono.empty(); // Test implementation
		}

		@Override
		public <T> T unmarshalFrom(Object value, TypeReference<T> type) {
			return null; // Test implementation
		}

		@Override
		public void close() {
			// Test implementation
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.empty(); // Test implementation
		}

	}

	@Configuration
	static class CustomTransportConfiguration {

		@Bean
		McpServerTransport customTransport() {
			return new CustomServerTransport();
		}

	}

}
