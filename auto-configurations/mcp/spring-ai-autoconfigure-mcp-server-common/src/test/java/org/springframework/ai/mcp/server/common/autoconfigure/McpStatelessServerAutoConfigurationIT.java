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
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.StatelessServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
public class McpStatelessServerAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STATELESS")
		.withConfiguration(AutoConfigurations.of(McpServerStatelessAutoConfiguration.class,
				StatelessToolCallbackConverterAutoConfiguration.class))
		.withUserConfiguration(TestStatelessTransportConfiguration.class);

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
			assertThat(context).hasSingleBean(McpStatelessServerTransport.class);

			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getName()).isEqualTo("mcp-server");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.getType()).isEqualTo(McpServerProperties.ApiType.SYNC);
			assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(20);
			// assertThat(properties.getMcpEndpoint()).isEqualTo("/mcp");

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
				assertThat(context).hasSingleBean(McpStatelessAsyncServer.class);
				assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);

				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getName()).isEqualTo("test-server");
				assertThat(properties.getVersion()).isEqualTo("2.0.0");
				assertThat(properties.getInstructions()).isEqualTo("My MCP Server");
				assertThat(properties.getType()).isEqualTo(McpServerProperties.ApiType.ASYNC);
				assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(30);
			});
	}

	@Test
	void syncToolCallbackRegistrationControl() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=SYNC", "spring.ai.mcp.server.tool-callback-converter=true")
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
	void syncServerInstructionsConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.instructions=Sync Server Instructions")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getInstructions()).isEqualTo("Sync Server Instructions");

				McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void disabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
			assertThat(context).doesNotHaveBean(McpStatelessAsyncServer.class);
			assertThat(context).doesNotHaveBean(McpStatelessServerTransport.class);
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
			McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Test
	void promptSpecificationConfiguration() {
		this.contextRunner.withUserConfiguration(TestPromptConfiguration.class).run(context -> {
			McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
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
			McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Test
	void asyncRootsChangeHandlerConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.type=ASYNC")
			.withUserConfiguration(TestAsyncRootsHandlerConfiguration.class)
			.run(context -> {
				McpStatelessAsyncServer server = context.getBean(McpStatelessAsyncServer.class);
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
				McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
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
				McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
				assertThat(server).isNotNull();
			});
	}

	@Test
	void requestTimeoutConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.request-timeout=45s").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getRequestTimeout().getSeconds()).isEqualTo(45);

			// Verify the server is configured with the timeout
			McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
			assertThat(server).isNotNull();
		});
	}

	@Test
	void endpointConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.endpoint=/my-mcp").run(context -> {
			McpServerProperties properties = context.getBean(McpServerProperties.class);
			// assertThat(properties.getMcpEndpoint()).isEqualTo("/my-mcp");

			// Verify the server is configured with the endpoints
			McpStatelessSyncServer server = context.getBean(McpStatelessSyncServer.class);
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

	@SuppressWarnings("unchecked")
	@Test
	void syncStatelessServerSpecificationConfiguration() {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					StatelessServerSpecificationFactoryAutoConfiguration.class)
			.withBean(SyncTestMcpSpecsComponent.class)
			.run(context -> {
				McpStatelessSyncServer syncServer = context.getBean(McpStatelessSyncServer.class);
				McpStatelessAsyncServer asyncServer = (McpStatelessAsyncServer) ReflectionTestUtils.getField(syncServer,
						"asyncServer");

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");
				assertThat(tools).hasSize(1);
				assertThat(tools.get(0).tool().name()).isEqualTo("add");

				ConcurrentHashMap<String, AsyncResourceSpecification> resources = (ConcurrentHashMap<String, AsyncResourceSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resources");
				assertThat(resources).hasSize(1);
				assertThat(resources.get("simple://static")).isNotNull();

				ConcurrentHashMap<String, AsyncResourceTemplateSpecification> resourceTemplates = (ConcurrentHashMap<String, AsyncResourceTemplateSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resourceTemplates");
				assertThat(resourceTemplates).hasSize(1);
				assertThat(resourceTemplates.get("config://{key}")).isNotNull();

				ConcurrentHashMap<String, AsyncPromptSpecification> prompts = (ConcurrentHashMap<String, AsyncPromptSpecification>) ReflectionTestUtils
					.getField(asyncServer, "prompts");
				assertThat(prompts).hasSize(1);
				assertThat(prompts.get("greeting")).isNotNull();

				ConcurrentHashMap<McpSchema.CompleteReference, McpStatelessServerFeatures.AsyncCompletionSpecification> completions = (ConcurrentHashMap<McpSchema.CompleteReference, McpStatelessServerFeatures.AsyncCompletionSpecification>) ReflectionTestUtils
					.getField(asyncServer, "completions");
				assertThat(completions).hasSize(1);
				assertThat(completions.keySet().iterator().next()).isInstanceOf(McpSchema.CompleteReference.class);
			});
	}

	@SuppressWarnings("unchecked")
	@Test
	void asyncStatelessServerSpecificationConfiguration() {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					StatelessServerSpecificationFactoryAutoConfiguration.class)
			.withBean(AsyncTestMcpSpecsComponent.class)
			.withPropertyValues("spring.ai.mcp.server.type=async")
			.run(context -> {
				McpStatelessAsyncServer asyncServer = context.getBean(McpStatelessAsyncServer.class);

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");
				assertThat(tools).hasSize(1);
				assertThat(tools.get(0).tool().name()).isEqualTo("add");

				ConcurrentHashMap<String, AsyncResourceSpecification> resources = (ConcurrentHashMap<String, AsyncResourceSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resources");
				assertThat(resources).hasSize(1);
				assertThat(resources.get("simple://static")).isNotNull();

				ConcurrentHashMap<String, AsyncResourceTemplateSpecification> resourceTemplates = (ConcurrentHashMap<String, AsyncResourceTemplateSpecification>) ReflectionTestUtils
					.getField(asyncServer, "resourceTemplates");
				assertThat(resourceTemplates).hasSize(1);
				assertThat(resourceTemplates.get("config://{key}")).isNotNull();

				ConcurrentHashMap<String, AsyncPromptSpecification> prompts = (ConcurrentHashMap<String, AsyncPromptSpecification>) ReflectionTestUtils
					.getField(asyncServer, "prompts");
				assertThat(prompts).hasSize(1);
				assertThat(prompts.get("greeting")).isNotNull();

				ConcurrentHashMap<McpSchema.CompleteReference, McpStatelessServerFeatures.AsyncCompletionSpecification> completions = (ConcurrentHashMap<McpSchema.CompleteReference, McpStatelessServerFeatures.AsyncCompletionSpecification>) ReflectionTestUtils
					.getField(asyncServer, "completions");
				assertThat(completions).hasSize(1);
				assertThat(completions.keySet().iterator().next()).isInstanceOf(McpSchema.CompleteReference.class);
			});
	}

	@SuppressWarnings("unchecked")
	@Test
	void logsWarningWhenSyncStatelessToolUsesUnsupportedContextParameter(CapturedOutput output) {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					StatelessServerSpecificationFactoryAutoConfiguration.class)
			.withBean(SyncUnsupportedContextToolComponent.class)
			.run(context -> {
				McpStatelessSyncServer syncServer = context.getBean(McpStatelessSyncServer.class);
				McpStatelessAsyncServer asyncServer = (McpStatelessAsyncServer) ReflectionTestUtils.getField(syncServer,
						"asyncServer");

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");
				assertThat(tools).isEmpty();

				assertThat(output).contains("MCP stateless mode skipped @McpTool method");
				assertThat(output).contains("SyncUnsupportedContextToolComponent");
				assertThat(output).contains("McpSyncRequestContext");
			});
	}

	@SuppressWarnings("unchecked")
	@Test
	void logsWarningWhenAsyncStatelessToolUsesUnsupportedContextParameter(CapturedOutput output) {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					StatelessServerSpecificationFactoryAutoConfiguration.class)
			.withBean(AsyncUnsupportedContextToolComponent.class)
			.withPropertyValues("spring.ai.mcp.server.type=async")
			.run(context -> {
				McpStatelessAsyncServer asyncServer = context.getBean(McpStatelessAsyncServer.class);

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");
				assertThat(tools).isEmpty();

				assertThat(output).contains("MCP stateless mode skipped @McpTool method");
				assertThat(output).contains("AsyncUnsupportedContextToolComponent");
				assertThat(output).contains("McpAsyncRequestContext");
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

			return List.of(SyncMcpToolCallback.builder().mcpClient(mockClient).tool(mockTool).build());
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

				return new ToolCallback[] {
						SyncMcpToolCallback.builder().mcpClient(mockClient).tool(mockTool).build() };
			};
		}

	}

	@Configuration
	static class TestCompletionConfiguration {

		@Bean
		List<SyncCompletionSpecification> testCompletions() {

			BiFunction<McpTransportContext, McpSchema.CompleteRequest, McpSchema.CompleteResult> completionHandler = (
					context, request) -> new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(List.of(), 0, false));

			return List.of(new McpStatelessServerFeatures.SyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code_review", "Code review"), completionHandler));
		}

	}

	@Configuration
	static class TestAsyncCompletionConfiguration {

		@Bean
		List<AsyncCompletionSpecification> testAsyncCompletions() {
			BiFunction<McpTransportContext, McpSchema.CompleteRequest, Mono<McpSchema.CompleteResult>> completionHandler = (
					context, request) -> Mono.just(new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(List.of(), 0, false)));

			return List.of(new McpStatelessServerFeatures.AsyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code_review", "Code review"), completionHandler));
		}

	}

	@Configuration
	static class TestRootsHandlerConfiguration {

		@Bean
		BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootsChangeHandler() {
			return (context, roots) -> {
				// Test implementation
			};
		}

	}

	@Configuration
	static class TestAsyncRootsHandlerConfiguration {

		@Bean
		BiConsumer<McpTransportContext, List<McpSchema.Root>> rootsChangeHandler() {
			return (context, roots) -> {
				// Test implementation
			};
		}

	}

	@Configuration
	static class TestStatelessTransportConfiguration {

		@Bean
		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		public McpStatelessServerTransport statelessTransport() {
			return Mockito.mock(McpStatelessServerTransport.class);
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

	@Component
	static class SyncUnsupportedContextToolComponent {

		@McpTool(name = "unsupported-sync-tool", description = "Uses unsupported stateless context param")
		public String unsupportedSyncTool(McpSyncRequestContext context) {
			return "ok";
		}

	}

	@Component
	static class AsyncUnsupportedContextToolComponent {

		@McpTool(name = "unsupported-async-tool", description = "Uses unsupported stateless context param")
		public Mono<String> unsupportedAsyncTool(McpAsyncRequestContext context) {
			return Mono.just("ok");
		}

	}

}
