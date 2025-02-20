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

package org.springframework.ai.autoconfigure.mcp.client;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.ClientMcpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import org.springframework.ai.autoconfigure.mcp.client.configurer.McpSyncClientConfigurer;
import org.springframework.ai.autoconfigure.mcp.client.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
public class McpClientAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class).run(context -> {
			List<McpSyncClient> clients = context.getBean("mcpSyncClients", List.class);
			assertThat(clients).hasSize(1);

			McpClientCommonProperties properties = context.getBean(McpClientCommonProperties.class);
			assertThat(properties.getName()).isEqualTo("mcp-client");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.getType()).isEqualTo(McpClientCommonProperties.ClientType.SYNC);
			assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(properties.isInitialized()).isTrue();
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

	@Test
	void customTransportConfiguration() {
		this.contextRunner.withUserConfiguration(CustomTransportConfiguration.class).run(context -> {
			List<NamedClientMcpTransport> transports = context.getBean("customTransports", List.class);
			assertThat(transports).hasSize(1);
			assertThat(transports.get(0).transport()).isInstanceOf(CustomClientTransport.class);
		});
	}

	@Test
	void clientCustomization() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class, CustomizerConfiguration.class)
			.run(context -> {
				assertThat(context).hasSingleBean(McpSyncClientConfigurer.class);
				List<McpSyncClient> clients = context.getBean("mcpSyncClients", List.class);
				assertThat(clients).hasSize(1);
			});
	}

	@Test
	void toolCallbacksCreation() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(List.class);
			List<ToolCallback> callbacks = context.getBean("toolCallbacks", List.class);
			assertThat(callbacks).isNotEmpty();
		});
	}

	@Test
	void closeableWrappersCreation() {
		this.contextRunner.withUserConfiguration(TestTransportConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(McpClientAutoConfiguration.CloseableMcpSyncClients.class);
		});
	}

	@Configuration
	static class TestTransportConfiguration {

		@Bean
		List<NamedClientMcpTransport> testTransports() {
			return List.of(new NamedClientMcpTransport("test", Mockito.mock(ClientMcpTransport.class)));
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

	static class CustomClientTransport implements ClientMcpTransport {

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
		public <T> T unmarshalFrom(Object value, TypeReference<T> type) {
			return null; // Test implementation
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.empty(); // Test implementation
		}

	}

}
