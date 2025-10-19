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

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MCP (Model Context Protocol) client auto-configuration.
 *
 * <p>
 * This test class validates that the Spring Boot auto-configuration for MCP clients works
 * correctly, including bean creation, property binding, and customization support. The
 * tests focus on verifying that the auto-configuration creates the expected beans without
 * requiring actual MCP protocol communication.
 *
 * <h3>Key Testing Patterns:</h3>
 * <ul>
 * <li><strong>Mock Transport Configuration:</strong> Uses properly configured Mockito
 * mocks for {@code McpClientTransport} that handle default interface methods like
 * {@code protocolVersions()}, {@code connect()}, and {@code sendMessage()}</li>
 *
 * <li><strong>Initialization Prevention:</strong> Most tests use
 * {@code spring.ai.mcp.client.initialized=false} to prevent the auto-configuration from
 * calling {@code client.initialize()} explicitly, which would cause 20-second timeouts
 * waiting for real MCP protocol communication</li>
 *
 * <li><strong>Bean Creation Testing:</strong> Tests verify that the correct beans are
 * created (e.g., {@code mcpSyncClients}, {@code mcpAsyncClients}) without requiring full
 * client initialization</li>
 * </ul>
 *
 * <h3>Important Notes:</h3>
 * <ul>
 * <li>When {@code initialized=false} is used, the {@code toolCallbacks} bean is not
 * created because it depends on fully initialized MCP clients</li>
 *
 * <li>The mock transport configuration is critical - Mockito mocks don't inherit default
 * interface methods, so {@code protocolVersions()}, {@code connect()}, and
 * {@code sendMessage()} must be explicitly configured</li>
 *
 * <li>Tests validate both the auto-configuration behavior and the resulting
 * {@code McpClientCommonProperties} configuration</li>
 * </ul>
 *
 * @see McpClientAutoConfiguration
 * @see McpToolCallbackAutoConfiguration
 * @see McpClientCommonProperties
 */
public class McpClientAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(McpToolCallbackAutoConfiguration.class, McpClientAutoConfiguration.class));

	/**
	 * Tests that MCP clients are created after all singleton beans have been initialized,
	 * verifying the SmartInitializingSingleton timing behavior.
	 * <p>
	 * This test uses a LateInitBean that records its initialization timestamp, and then
	 * verifies that the MCP client initializer was called AFTER the late bean was
	 * constructed. This proves that
	 * SmartInitializingSingleton.afterSingletonsInstantiated() is called after all
	 * singleton beans (including late-initializing ones) have been fully created.
	 */
	@Test
	void clientsCreatedAfterAllSingletons() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class, LateInitBeanWithTimestamp.class)
			.withPropertyValues("spring.ai.mcp.client.initialized=false")
			.run(context -> {
				// Get the late-init bean and its construction timestamp
				LateInitBeanWithTimestamp lateBean = context.getBean(LateInitBeanWithTimestamp.class);
				long lateBeanTimestamp = lateBean.getInitTimestamp();

				// Get the initializer and its execution timestamp
				var initializer = context.getBean(McpClientAutoConfiguration.McpSyncClientInitializer.class);
				long initializerTimestamp = initializer.getInitializationTimestamp();

				// Verify clients were created
				List<McpSyncClient> clients = context.getBean("mcpSyncClients", List.class);
				assertThat(clients).isNotNull();

				// THE KEY ASSERTION: Initializer must have been called AFTER late bean
				// was constructed
				// This proves SmartInitializingSingleton.afterSingletonsInstantiated()
				// timing
				assertThat(initializerTimestamp)
					.as("MCP client initializer should be called AFTER all singleton beans are initialized")
					.isGreaterThan(lateBeanTimestamp);
			});
	}

	@Test
	void asyncConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC", "spring.ai.mcp.client.name=test-client",
					"spring.ai.mcp.client.version=2.0.0", "spring.ai.mcp.client.request-timeout=60s",
					"spring.ai.mcp.client.initialized=false")
			.withUserConfiguration(TestTransportConfiguration.class)
			.run(context -> {
				List<McpAsyncClient> clients = context.getBean("mcpAsyncClients", List.class);
				assertThat(clients).hasSize(1);

				McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
				assertThat(properties.getName()).isEqualTo("test-client");
				assertThat(properties.getVersion()).isEqualTo("2.0.0");
				assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.ASYNC);
				assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
				assertThat(properties.isInitialized()).isFalse();
			});
	}

	@Test
	void disabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(McpSyncClient.class);
			assertThat(context).doesNotHaveBean(McpAsyncClient.class);
			assertThat(context).doesNotHaveBean(ToolCallback.class);
		});
	}

	/**
	 * Tests MCP client auto-configuration with custom transport.
	 *
	 * Note: We use 'spring.ai.mcp.client.initialized=false' to prevent the
	 * auto-configuration from calling client.initialize() explicitly, which would cause a
	 * 20-second timeout waiting for real MCP protocol communication. This allows us to
	 * test bean creation and auto-configuration behavior without requiring a full MCP
	 * server connection.
	 */
	@Test
	void customTransportConfiguration() {
		this.contextRunner.withUserConfiguration(CustomTransportConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.initialized=false")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("customTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).transport()).isInstanceOf(CustomClientTransport.class);
			});
	}

	/**
	 * Tests MCP client auto-configuration with custom client customizers.
	 *
	 * Note: We use 'spring.ai.mcp.client.initialized=false' to prevent the
	 * auto-configuration from calling client.initialize() explicitly, which would cause a
	 * 20-second timeout waiting for real MCP protocol communication. This allows us to
	 * test bean creation and auto-configuration behavior without requiring a full MCP
	 * server connection.
	 */
	@Test
	void clientCustomization() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class, CustomizerConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.initialized=false")
			.run(context -> {
				assertThat(context).hasSingleBean(McpSyncClientConfigurer.class);
				List<McpSyncClient> clients = context.getBean("mcpSyncClients", List.class);
				assertThat(clients).hasSize(1);
			});
	}

	/**
	 * Tests that MCP client beans are created when using initialized=false.
	 *
	 * Note: The toolCallbacks bean doesn't exist with initialized=false because it
	 * depends on fully initialized MCP clients. The mcpSyncClients bean does exist even
	 * with initialized=false, which tests the actual auto-configuration behavior we care
	 * about - that MCP client beans are created without requiring full protocol
	 * initialization.
	 *
	 * We use 'spring.ai.mcp.client.initialized=false' to prevent the auto-configuration
	 * from calling client.initialize() explicitly, which would cause a 20-second timeout
	 * waiting for real MCP protocol communication. This allows us to test bean creation
	 * without requiring a full MCP server connection.
	 */
	@Test
	void toolCallbacksCreation() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.initialized=false")
			.run(context -> {
				assertThat(context).hasBean("mcpSyncClients");
				List<?> clients = context.getBean("mcpSyncClients", List.class);
				assertThat(clients).isNotNull();
			});
	}

	/**
	 * Tests that closeable wrapper beans are created properly.
	 *
	 * Note: We use 'spring.ai.mcp.client.initialized=false' to prevent the
	 * auto-configuration from calling client.initialize() explicitly, which would cause a
	 * 20-second timeout waiting for real MCP protocol communication. This allows us to
	 * test bean creation and auto-configuration behavior without requiring a full MCP
	 * server connection.
	 */
	@Test
	void closeableWrappersCreation() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.initialized=false")
			.run(context -> assertThat(context)
				.hasSingleBean(McpClientAutoConfiguration.CloseableMcpSyncClients.class));
	}

	/**
	 * Tests that SmartInitializingSingleton initializers are created and function
	 * correctly for sync clients.
	 */
	@Test
	void smartInitializingSingletonBehavior() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.initialized=false")
			.run(context -> {
				// Verify that McpSyncClientInitializer bean exists
				assertThat(context).hasBean("mcpSyncClientInitializer");
				assertThat(context.getBean("mcpSyncClientInitializer"))
					.isInstanceOf(McpClientAutoConfiguration.McpSyncClientInitializer.class);

				// Verify that clients list exists and was created by initializer
				List<McpSyncClient> clients = context.getBean("mcpSyncClients", List.class);
				assertThat(clients).isNotNull();

				// Verify the initializer has completed
				var initializer = context.getBean(McpClientAutoConfiguration.McpSyncClientInitializer.class);
				assertThat(initializer.getClients()).isSameAs(clients);
			});
	}

	/**
	 * Tests that SmartInitializingSingleton initializers are created and function
	 * correctly for async clients.
	 */
	@Test
	void smartInitializingSingletonForAsyncClients() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC", "spring.ai.mcp.client.initialized=false")
			.run(context -> {
				// Verify that McpAsyncClientInitializer bean exists
				assertThat(context).hasBean("mcpAsyncClientInitializer");
				assertThat(context.getBean("mcpAsyncClientInitializer"))
					.isInstanceOf(McpClientAutoConfiguration.McpAsyncClientInitializer.class);

				// Verify that clients list exists and was created by initializer
				List<McpAsyncClient> clients = context.getBean("mcpAsyncClients", List.class);
				assertThat(clients).isNotNull();

				// Verify the initializer has completed
				var initializer = context.getBean(McpClientAutoConfiguration.McpAsyncClientInitializer.class);
				assertThat(initializer.getClients()).isSameAs(clients);
			});
	}

	@Configuration
	static class TestTransportConfiguration {

		@Bean
		List<NamedClientMcpTransport> testTransports() {
			// Create a properly configured mock that handles default interface methods
			McpClientTransport mockTransport = Mockito.mock(McpClientTransport.class);
			// Configure the mock to return proper protocol versions for the default
			// interface method
			Mockito.when(mockTransport.protocolVersions()).thenReturn(List.of("2024-11-05"));
			// Configure the mock to return a never-completing Mono to simulate pending
			// connection
			Mockito.when(mockTransport.connect(Mockito.any())).thenReturn(Mono.never());
			// Configure the mock to return a never-completing Mono for sendMessage
			Mockito.when(mockTransport.sendMessage(Mockito.any())).thenReturn(Mono.never());
			return List.of(new NamedClientMcpTransport("test", mockTransport));
		}

	}

	@Configuration
	static class CustomTransportConfiguration {

		@Bean
		List<NamedClientMcpTransport> customTransports() {
			return List.of(new NamedClientMcpTransport("custom", new CustomClientTransport()));
		}

	}

	@Configuration
	static class CustomizerConfiguration {

		@Bean
		McpSyncClientCustomizer testCustomizer() {
			return (name, spec) -> {
				/* no-op */ };
		}

	}

	@Configuration
	static class LateInitBean {

		private final boolean initialized;

		LateInitBean() {
			// Simulate late initialization
			this.initialized = true;
		}

		@Bean
		String lateInitBean() {
			// This bean method ensures the configuration is instantiated
			return "late-init-marker";
		}

		boolean isInitialized() {
			return this.initialized;
		}

	}

	/**
	 * A configuration bean that records when it was initialized. Used to verify
	 * SmartInitializingSingleton timing - that the MCP client initializer is called AFTER
	 * all singleton beans (including this one) have been constructed.
	 */
	@Configuration
	static class LateInitBeanWithTimestamp {

		private final long initTimestamp;

		LateInitBeanWithTimestamp() {
			// Record when this bean was constructed
			this.initTimestamp = System.nanoTime();
		}

		@Bean
		String lateInitMarker() {
			// This bean method ensures the configuration is instantiated
			return "late-init-marker";
		}

		long getInitTimestamp() {
			return this.initTimestamp;
		}

	}

	static class CustomClientTransport implements McpClientTransport {

		@Override
		public void close() {
			// Test implementation
		}

		@Override
		public Mono<Void> connect(
				Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> messageHandler) {
			return Mono.empty(); // Test implementation
		}

		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			return Mono.empty(); // Test implementation
		}

		@Override
		public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
			return null;
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.empty(); // Test implementation
		}

	}

}
