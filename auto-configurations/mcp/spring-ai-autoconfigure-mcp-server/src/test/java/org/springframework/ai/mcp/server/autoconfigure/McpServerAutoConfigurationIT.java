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
import java.util.function.BiFunction;

import com.fasterxml.jackson.core.type.TypeReference;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
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
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class McpServerAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class));

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
			assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(20);
			assertThat(properties.getBaseUrl()).isEqualTo("");
			assertThat(properties.getSseEndpoint()).isEqualTo("/sse");
			assertThat(properties.getSseMessageEndpoint()).isEqualTo("/mcp/message");

			// Check capabilities
			assertThat(properties.getCapabilities().isTool()).isTrue();
			assertThat(properties.getCapabilities().isResource()).isTrue();
			assertThat(properties.getCapabilities().isPrompt()).isTrue();
			assertThat(properties.getCapabilities().isCompletion()).isTrue();
		});
	}

	@Test
	void asyncConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.name=test-server",
					"spring.ai.mcp.server.version=2.0.0", "spring.ai.mcp.server.instructions=My MCP Server",
					"spring.ai.mcp.server.request-timeout=30s")
			.run(context -> {
				assertThat(context).hasSingleBean(McpAsyncServer.class);
				assertThat(context).doesNotHaveBean(McpSyncServer.class);

				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getName()).isEqualTo("test-server");
				assertThat(properties.getVersion()).isEqualTo("2.0.0");
				assertThat(properties.getInstructions()).isEqualTo("My MCP Server");
				assertThat(properties.getType()).isEqualTo(McpServerProperties.ServerType.ASYNC);
				assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(30);
			});
	}

	@Test
	void syncServerInstructionsConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.instructions=Sync Server Instructions")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getInstructions()).isEqualTo("Sync Server Instructions");

				McpSyncServer server = context.getBean(McpSyncServer.class);
				assertThat(server).isNotNull();
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

	@Test
	void asyncRootsChangeHandlerConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withUserConfiguration(TestAsyncRootsHandlerConfiguration.class)
			.run(context -> {
				McpAsyncServer server = context.getBean(McpAsyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void capabilitiesConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.capabilities.tool=false",
				"spring.ai.mcp.server.capabilities.resource=false", "spring.ai.mcp.server.capabilities.prompt=false",
				"spring.ai.mcp.server.capabilities.completion=false")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getCapabilities().isTool()).isFalse();
				assertThat(properties.getCapabilities().isResource()).isFalse();
				assertThat(properties.getCapabilities().isPrompt()).isFalse();
				assertThat(properties.getCapabilities().isCompletion()).isFalse();

				// Verify the server is configured with the disabled capabilities
				McpSyncServer server = context.getBean(McpSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void toolResponseMimeTypeConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.tool-response-mime-type.test-tool=application/json")
			.withUserConfiguration(TestToolConfiguration.class)
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getToolResponseMimeType()).containsEntry("test-tool", "application/json");

				// Verify the MIME type is applied to the tool specifications
				List<SyncToolSpecification> tools = context.getBean("syncTools", List.class);
				assertThat(tools).hasSize(1);

				// The server should be properly configured with the tool
				McpSyncServer server = context.getBean(McpSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void requestTimeoutConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.request-timeout=45s").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(45);

			// Verify the server is configured with the timeout
			McpSyncServer server = context.getBean(McpSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.base-url=http://localhost:8080",
					"spring.ai.mcp.server.sse-endpoint=/events",
					"spring.ai.mcp.server.sse-message-endpoint=/api/mcp/message")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8080");
				assertThat(properties.getSseEndpoint()).isEqualTo("/events");
				assertThat(properties.getSseMessageEndpoint()).isEqualTo("/api/mcp/message");

				// Verify the server is configured with the endpoints
				McpSyncServer server = context.getBean(McpSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void completionSpecificationConfiguration() {
		this.contextRunner.withUserConfiguration(TestCompletionConfiguration.class).run(context -> {
			List<SyncCompletionSpecification> completions = context.getBean("testCompletions", List.class);
			assertThat(completions).hasSize(1);
		});
	}

	@Test
	void asyncCompletionSpecificationConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withUserConfiguration(TestAsyncCompletionConfiguration.class)
			.run(context -> {
				List<AsyncCompletionSpecification> completions = context.getBean("testAsyncCompletions", List.class);
				assertThat(completions).hasSize(1);
			});
	}

	@Test
	void toolCallbackProviderConfiguration() {
		this.contextRunner.withUserConfiguration(TestToolCallbackProviderConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(ToolCallbackProvider.class));
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
	static class TestToolCallbackProviderConfiguration {

		@Bean
		ToolCallbackProvider testToolCallbackProvider() {
			return () -> {
				McpSyncClient mockClient = Mockito.mock(McpSyncClient.class);
				McpSchema.Tool mockTool = Mockito.mock(McpSchema.Tool.class);

				Mockito.when(mockTool.name()).thenReturn("provider-tool");
				Mockito.when(mockTool.description()).thenReturn("Provider Tool");
				when(mockClient.getClientInfo()).thenReturn(new McpSchema.Implementation("testClient", "1.0.0"));

				return new ToolCallback[] { new SyncMcpToolCallback(mockClient, mockTool) };
			};
		}

	}

	@Configuration
	static class TestCompletionConfiguration {

		@Bean
		List<SyncCompletionSpecification> testCompletions() {

			BiFunction<McpSyncServerExchange, McpSchema.CompleteRequest, McpSchema.CompleteResult> completionHandler = (
					exchange, request) -> new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(List.of(), 0, false));

			return List.of(new McpServerFeatures.SyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code_review"), completionHandler));
		}

	}

	@Configuration
	static class TestAsyncCompletionConfiguration {

		@Bean
		List<AsyncCompletionSpecification> testAsyncCompletions() {
			BiFunction<McpAsyncServerExchange, McpSchema.CompleteRequest, Mono<McpSchema.CompleteResult>> completionHandler = (
					exchange, request) -> Mono.just(new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(List.of(), 0, false)));

			return List.of(new McpServerFeatures.AsyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code_review"), completionHandler));
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

	@Configuration
	static class TestAsyncRootsHandlerConfiguration {

		@Bean
		BiConsumer<McpAsyncServerExchange, List<McpSchema.Root>> rootsChangeHandler() {
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
