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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceTemplateSpecification;
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
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerChangeNotificationProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class McpServerAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerObjectMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class));

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
			assertThat(properties.getType()).isEqualTo(McpServerProperties.ApiType.SYNC);
			assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(20);

			// Check capabilities
			assertThat(properties.getCapabilities().isTool()).isTrue();
			assertThat(properties.getCapabilities().isResource()).isTrue();
			assertThat(properties.getCapabilities().isPrompt()).isTrue();
			assertThat(properties.getCapabilities().isCompletion()).isTrue();

			// Check change notifications
			assertThat(properties.getToolChangeNotification().isToolChangeNotification()).isTrue();
			assertThat(properties.getToolChangeNotification().isResourceChangeNotification()).isTrue();
			assertThat(properties.getToolChangeNotification().isPromptChangeNotification()).isTrue();

			// Check default nested configurations exist
			assertThat(properties.getSse()).isNotNull();
			assertThat(properties.getStreamable()).isNotNull();
			assertThat(properties.getStateless()).isNotNull();

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
				assertThat(properties.getType()).isEqualTo(McpServerProperties.ApiType.ASYNC);
				assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(30);
			});
	}

	@Test
	void protocolSwitchingConfiguration() {
		// Test SSE protocol (default)
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=SSE").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getProtocol()).isEqualTo(McpServerProperties.ServerProtocol.SSE);
		});

		// Test STREAMABLE protocol
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getProtocol()).isEqualTo(McpServerProperties.ServerProtocol.STREAMABLE);
		});

		// Test STATELESS protocol
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getProtocol()).isEqualTo(McpServerProperties.ServerProtocol.STATELESS);
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
				assertThat(properties.getToolChangeNotification().isToolChangeNotification()).isFalse();
				assertThat(properties.getToolChangeNotification().isResourceChangeNotification()).isFalse();
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
				assertThat(properties.getToolChangeNotification().isToolChangeNotification()).isFalse();
				assertThat(properties.getToolChangeNotification().isResourceChangeNotification()).isFalse();
				assertThat(properties.getToolChangeNotification().isPromptChangeNotification()).isFalse();
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
			List<McpServerFeatures.SyncToolSpecification> tools = context.getBean("syncTools", List.class);
			assertThat(tools).hasSize(1);
		});
	}

	@Test
	void syncToolCallbackRegistrationControl() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server..type=SYNC", "spring.ai.mcp.server..tool-callback-converter=true")
			.run(context -> assertThat(context).hasBean("syncTools"));

		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=SYNC", "spring.ai.mcp.server.tool-callback-converter=false")
			.run(context -> assertThat(context).doesNotHaveBean("syncTools"));
	}

	@Test
	void asyncToolCallbackRegistrationControl() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.tool-callback-converter=true")
			.run(context -> assertThat(context).hasBean("asyncTools"));
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.tool-callback-converter=false")
			.run(context -> assertThat(context).doesNotHaveBean("asyncTools"));
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
				List<McpServerFeatures.AsyncToolSpecification> tools = context.getBean("asyncTools", List.class);
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
				List<McpServerFeatures.SyncToolSpecification> tools = context.getBean("syncTools", List.class);
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
	void completionSpecificationConfiguration() {
		this.contextRunner.withUserConfiguration(TestCompletionConfiguration.class).run(context -> {
			List<McpServerFeatures.SyncCompletionSpecification> completions = context.getBean("testCompletions",
					List.class);
			assertThat(completions).hasSize(1);
		});
	}

	@Test
	void asyncCompletionSpecificationConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withUserConfiguration(TestAsyncCompletionConfiguration.class)
			.run(context -> {
				List<McpServerFeatures.AsyncCompletionSpecification> completions = context
					.getBean("testAsyncCompletions", List.class);
				assertThat(completions).hasSize(1);
			});
	}

	@Test
	void toolCallbackProviderConfiguration() {
		this.contextRunner.withUserConfiguration(TestToolCallbackProviderConfiguration.class)
			.run(context -> assertThat(context).hasSingleBean(ToolCallbackProvider.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	void syncServerSpecificationConfiguration() {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					McpServerSpecificationFactoryAutoConfiguration.class)
			.withBean(SyncTestMcpSpecsComponent.class)
			.run(context -> {
				McpSyncServer syncServer = context.getBean(McpSyncServer.class);
				McpAsyncServer asyncServer = (McpAsyncServer) ReflectionTestUtils.getField(syncServer, "asyncServer");

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");
				assertThat(tools).hasSize(1);
				assertThat(tools.get(0).tool().name()).isEqualTo("add");

				ConcurrentHashMap<String, AsyncResourceSpecification> resources = (ConcurrentHashMap<String, AsyncResourceSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resources");
				assertThat(resources).hasSize(1);
				assertThat(resources.get("simple://static")).isNotNull();

				ConcurrentHashMap<String, AsyncResourceTemplateSpecification> resourceTemplatess = (ConcurrentHashMap<String, AsyncResourceTemplateSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resourceTemplates");
				assertThat(resourceTemplatess).hasSize(1);
				assertThat(resourceTemplatess.get("config://{key}")).isNotNull();

				ConcurrentHashMap<String, AsyncPromptSpecification> prompts = (ConcurrentHashMap<String, AsyncPromptSpecification>) ReflectionTestUtils
					.getField(asyncServer, "prompts");
				assertThat(prompts).hasSize(1);
				assertThat(prompts.get("greeting")).isNotNull();

				ConcurrentHashMap<McpSchema.CompleteReference, AsyncCompletionSpecification> completions = (ConcurrentHashMap<McpSchema.CompleteReference, AsyncCompletionSpecification>) ReflectionTestUtils
					.getField(asyncServer, "completions");
				assertThat(completions).hasSize(1);
				assertThat(completions.keySet().iterator().next()).isInstanceOf(McpSchema.CompleteReference.class);
			});
	}

	@SuppressWarnings("unchecked")
	@Test
	void asyncServerSpecificationConfiguration() {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					McpServerSpecificationFactoryAutoConfiguration.class)
			.withBean(AsyncTestMcpSpecsComponent.class)
			.withPropertyValues("spring.ai.mcp.server.type=async")
			.run(context -> {
				McpAsyncServer asyncServer = context.getBean(McpAsyncServer.class);

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");
				assertThat(tools).hasSize(1);
				assertThat(tools.get(0).tool().name()).isEqualTo("add");

				ConcurrentHashMap<String, AsyncResourceSpecification> resources = (ConcurrentHashMap<String, AsyncResourceSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resources");
				assertThat(resources).hasSize(1);
				assertThat(resources.get("simple://static")).isNotNull();

				ConcurrentHashMap<String, AsyncResourceTemplateSpecification> resourceTemplatess = (ConcurrentHashMap<String, AsyncResourceTemplateSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resourceTemplates");
				assertThat(resourceTemplatess).hasSize(1);
				assertThat(resourceTemplatess.get("config://{key}")).isNotNull();

				ConcurrentHashMap<String, AsyncPromptSpecification> prompts = (ConcurrentHashMap<String, AsyncPromptSpecification>) ReflectionTestUtils
					.getField(asyncServer, "prompts");
				assertThat(prompts).hasSize(1);
				assertThat(prompts.get("greeting")).isNotNull();

				ConcurrentHashMap<McpSchema.CompleteReference, AsyncCompletionSpecification> completions = (ConcurrentHashMap<McpSchema.CompleteReference, AsyncCompletionSpecification>) ReflectionTestUtils
					.getField(asyncServer, "completions");
				assertThat(completions).hasSize(1);
				assertThat(completions.keySet().iterator().next()).isInstanceOf(McpSchema.CompleteReference.class);
			});
	}

	@Configuration
	static class TestResourceConfiguration {

		@Bean
		List<McpServerFeatures.SyncResourceSpecification> testResources() {
			return List.of();
		}

	}

	@Configuration
	static class TestPromptConfiguration {

		@Bean
		List<McpServerFeatures.SyncPromptSpecification> testPrompts() {
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

			return List.of(SyncMcpToolCallback.builder()
				.mcpClient(mockClient)
				.tool(mockTool)
				.prefixedToolName(mockTool.name())
				.build());
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

				return new ToolCallback[] { SyncMcpToolCallback.builder()
					.mcpClient(mockClient)
					.tool(mockTool)
					.prefixedToolName(mockTool.name())
					.build() };
			};
		}

	}

	@Configuration
	static class TestCompletionConfiguration {

		@Bean
		List<McpServerFeatures.SyncCompletionSpecification> testCompletions() {

			BiFunction<McpSyncServerExchange, McpSchema.CompleteRequest, McpSchema.CompleteResult> completionHandler = (
					exchange, request) -> new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(List.of(), 0, false));

			return List.of(new McpServerFeatures.SyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code_review", "Code review"), completionHandler));
		}

	}

	@Configuration
	static class TestAsyncCompletionConfiguration {

		@Bean
		List<McpServerFeatures.AsyncCompletionSpecification> testAsyncCompletions() {
			BiFunction<McpAsyncServerExchange, McpSchema.CompleteRequest, Mono<McpSchema.CompleteResult>> completionHandler = (
					exchange, request) -> Mono.just(new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(List.of(), 0, false)));

			return List.of(new McpServerFeatures.AsyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code_review", "Code review"), completionHandler));
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
		public <T> T unmarshalFrom(Object data, TypeRef<T> type) {
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

	@Component
	static class SyncTestMcpSpecsComponent {

		@McpTool(name = "add", description = "Add two numbers together", title = "Add Two Numbers Together",
				annotations = @McpTool.McpAnnotations(title = "Rectangle Area Calculator", readOnlyHint = true,
						destructiveHint = false, idempotentHint = true))
		public int add(@McpToolParam(description = "First number", required = true) int a,
				@McpToolParam(description = "Second number", required = true) int b) {
			return a + b;
		}

		@McpResource(uri = "simple://static", name = "Configuration", description = "Provides configuration data")
		public String getSimple() {
			return "Hi there!";
		}

		@McpResource(uri = "config://{key}", name = "Configuration", description = "Provides configuration data")
		public String getConfig(String key) {
			return "config value";
		}

		@McpPrompt(name = "greeting", description = "Generate a greeting message")
		public McpSchema.GetPromptResult greeting(
				@McpArg(name = "name", description = "User's name", required = true) String name) {

			String message = "Hello, " + name + "! How can I help you today?";

			return new McpSchema.GetPromptResult("Greeting",
					List.of(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent(message))));
		}

		@McpComplete(prompt = "city-search")
		public List<String> completeCityName(String prefix) {
			return Stream.of("New York", "Los Angeles", "Chicago", "Houston", "Phoenix")
				.filter(city -> city.toLowerCase().startsWith(prefix.toLowerCase()))
				.limit(10)
				.toList();
		}

	}

	@Component
	static class AsyncTestMcpSpecsComponent {

		@McpTool(name = "add", description = "Add two numbers together", title = "Add Two Numbers Together",
				annotations = @McpTool.McpAnnotations(title = "Rectangle Area Calculator", readOnlyHint = true,
						destructiveHint = false, idempotentHint = true))
		public Mono<Integer> add(@McpToolParam(description = "First number", required = true) int a,
				@McpToolParam(description = "Second number", required = true) int b) {
			return Mono.just(a + b);
		}

		@McpResource(uri = "simple://static", name = "Configuration", description = "Provides configuration data")
		public Mono<String> getSimple() {
			return Mono.just("Hi there!");
		}

		@McpResource(uri = "config://{key}", name = "Configuration", description = "Provides configuration data")
		public Mono<String> getConfig(String key) {
			return Mono.just("config value");
		}

		@McpPrompt(name = "greeting", description = "Generate a greeting message")
		public Mono<McpSchema.GetPromptResult> greeting(
				@McpArg(name = "name", description = "User's name", required = true) String name) {

			String message = "Hello, " + name + "! How can I help you today?";

			return Mono.just(new McpSchema.GetPromptResult("Greeting", List
				.of(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent(message)))));
		}

		@McpComplete(prompt = "city-search")
		public Mono<List<String>> completeCityName(String prefix) {
			return Mono.just(Stream.of("New York", "Los Angeles", "Chicago", "Houston", "Phoenix")
				.filter(city -> city.toLowerCase().startsWith(prefix.toLowerCase()))
				.limit(10)
				.toList());
		}

	}

}
