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

package org.springframework.ai.mcp.client.webflux.autoconfigure;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpSampling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Daniel Garnier-Moiroux
 */
class McpToolsConfigurationTests {

	/**
	 * Test that MCP tools have handlers configured when they use a chat client. This
	 * verifies that there is no cyclic dependency
	 * {@code McpClient -> @McpHandling -> ChatClient -> McpClient}.
	 */
	@Test
	void mcpClientSupportsSampling() {
		//@formatter:off
		var clientApplicationContext = new ApplicationContextRunner()
			.withUserConfiguration(TestMcpClientHandlers.class)
			// Create a transport
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:0",
					"spring.ai.mcp.client.initialized=false")
			.withConfiguration(AutoConfigurations.of(
					// WebClientFactory
					DefaultWebClientFactory.class,
					// Transport
					StreamableHttpWebFluxTransportAutoConfiguration.class,
					// MCP clients
					McpToolCallbackAutoConfiguration.class,
					McpClientAutoConfiguration.class,
					McpClientAnnotationScannerAutoConfiguration.class,
					// Tool callbacks
					ToolCallingAutoConfiguration.class,
					// Chat client for sampling
					ChatClientAutoConfiguration.class,
					ChatModelAutoConfiguration.class
			));
		//@formatter:on
		clientApplicationContext.run(ctx -> {
			// If the MCP callback provider is picked un by the
			// ToolCallingAutoConfiguration,
			// #getToolCallbacks will be called during the init phase, and try to call the
			// MCP server
			// There is no MCP server in this test, so the context would not even start.
			String[] clients = ctx
				.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<List<McpSyncClient>>() {
				}));
			assertThat(clients).hasSize(1);
			List<McpSyncClient> syncClients = (List<McpSyncClient>) ctx.getBean(clients[0]);
			assertThat(syncClients).hasSize(1)
				.first()
				.extracting(McpSyncClient::getClientCapabilities)
				.extracting(McpSchema.ClientCapabilities::sampling)
				.describedAs("Sampling")
				.isNotNull();
		});
	}

	/**
	 * Ensure that MCP-related {@link ToolCallbackProvider}s do not get their
	 * {@code getToolCallbacks} method called on startup, and that, when possible, they
	 * are not injected into the default {@link ToolCallbackResolver}.
	 */
	@Test
	void toolCallbacksRegistered() {
		var clientApplicationContext = new ApplicationContextRunner()
			.withUserConfiguration(TestToolCallbackConfiguration.class)
			.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class));

		clientApplicationContext.run(ctx -> {
			// Observable behavior
			var resolver = ctx.getBean(ToolCallbackResolver.class);

			// Resolves beans that are NOT mcp-related
			assertThat(resolver.resolve("toolCallbackProvider")).isNotNull();
			assertThat(resolver.resolve("customToolCallbackProvider")).isNotNull();

			// MCP toolcallback providers are never added to the resolver
			// Otherwise, they would throw.
		});
	}

	static class TestMcpClientHandlers {

		private static final Logger logger = LoggerFactory.getLogger(TestMcpClientHandlers.class);

		private final ChatClient chatClient;

		TestMcpClientHandlers(ChatClient.Builder clientBuilder) {
			this.chatClient = clientBuilder.build();
		}

		@McpSampling(clients = "server1")
		McpSchema.CreateMessageResult samplingHandler(McpSchema.CreateMessageRequest llmRequest) {
			logger.info("MCP SAMPLING: {}", llmRequest);

			String userPrompt = ((McpSchema.TextContent) llmRequest.messages().get(0).content()).text();
			String modelHint = llmRequest.modelPreferences().hints().get(0).name();
			// In a real use-case, we would use the chat client to call the LLM again
			logger.info("MCP SAMPLING: simulating using chat client {}", this.chatClient);

			return McpSchema.CreateMessageResult.builder()
				.content(new McpSchema.TextContent("Response " + userPrompt + " with model hint " + modelHint))
				.build();
		}

	}

	static class ChatModelAutoConfiguration {

		/**
		 * This is typically provided by a model-specific autoconfig, such as
		 * {@code AnthropicChatAutoConfiguration}. We do not need a full LLM in this test,
		 * so we mock out the chat model.
		 */
		@Bean
		ChatModel chatModel() {
			return mock(ChatModel.class);
		}

	}

	static class TestToolCallbackConfiguration {

		@Bean
		ToolCallbackProvider toolCallbackProvider() {
			var tcp = mock(ToolCallbackProvider.class);
			when(tcp.getToolCallbacks()).thenReturn(toolCallback("toolCallbackProvider"));
			return tcp;
		}

		@Bean
		CustomToolCallbackProvider customToolCallbackProvider() {
			return new CustomToolCallbackProvider("customToolCallbackProvider");
		}

		// Ignored by the resolver
		@Bean
		SyncMcpToolCallbackProvider mcpToolCallbackProvider(@Lazy ToolCallbackResolver resolver) {
			var tcp = mock(SyncMcpToolCallbackProvider.class);
			when(tcp.getToolCallbacks())
				.thenThrow(new RuntimeException("mcpToolCallbackProvider#getToolCallbacks should not be called"));
			return tcp;
		}

		// This bean depends on the resolver, to ensure there are no cyclic dependencies
		@Bean
		CustomMcpToolCallbackProvider customMcpToolCallbackProvider(@Lazy ToolCallbackResolver resolver) {
			return new CustomMcpToolCallbackProvider();
		}

		// Ignored by the resolver
		@Bean
		ToolCallbackProvider genericMcpToolCallbackProvider() {
			return new CustomMcpToolCallbackProvider();
		}

		static ToolCallback[] toolCallback(String name) {
			return new ToolCallback[] { new ToolCallback() {
				@Override
				public ToolDefinition getToolDefinition() {
					return ToolDefinition.builder()
						.name(name)
						.inputSchema(JsonSchemaGenerator.generateForType(String.class))
						.build();
				}

				@Override
				public String call(String toolInput) {
					return "~~ not implemented ~~";
				}
			} };
		}

		static class CustomToolCallbackProvider implements ToolCallbackProvider {

			private final String name;

			CustomToolCallbackProvider(String name) {
				this.name = name;
			}

			@Override
			public ToolCallback[] getToolCallbacks() {
				return toolCallback(this.name);
			}

		}

		static class CustomMcpToolCallbackProvider extends SyncMcpToolCallbackProvider {

			@Override
			public ToolCallback[] getToolCallbacks() {
				throw new RuntimeException("CustomMcpToolCallbackProvider#getToolCallbacks should not be called");
			}

		}

	}

}
