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

import java.lang.reflect.Field;
import java.util.List;

import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SseWebFluxTransportAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
public class SseWebFluxTransportAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SseWebFluxTransportAutoConfiguration.class));

	@Test
	void webFluxClientTransportsPresentIfWebFluxSseClientTransportPresent() {
		this.applicationContext.run(context -> assertThat(context.containsBean("sseWebFluxClientTransports")).isTrue());
	}

	@Test
	void webFluxClientTransportsNotPresentIfMissingWebFluxSseClientTransportNotPresent() {
		this.applicationContext
			.withClassLoader(
					new FilteredClassLoader("io.modelcontextprotocol.client.transport.WebFluxSseClientTransport"))
			.run(context -> assertThat(context.containsBean("sseWebFluxClientTransports")).isFalse());
	}

	@Test
	void webFluxClientTransportsNotPresentIfMcpClientDisabled() {
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.enabled", "false")
			.run(context -> assertThat(context.containsBean("sseWebFluxClientTransports")).isFalse());
	}

	@Test
	void noTransportsCreatedWithEmptyConnections() {
		this.applicationContext.run(context -> {
			List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
			assertThat(transports).isEmpty();
		});
	}

	@Test
	void singleConnectionCreatesOneTransport() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(WebFluxSseClientTransport.class);
			});
	}

	@Test
	void multipleConnectionsCreateMultipleTransports() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof WebFluxSseClientTransport);
				for (NamedClientMcpTransport transport : transports) {
					assertThat(transport.transport()).isInstanceOf(WebFluxSseClientTransport.class);
					assertThat(getSseEndpoint((WebFluxSseClientTransport) transport.transport())).isEqualTo("/sse");
				}
			});
	}

	@Test
	void customSseEndpointIsRespected() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=/custom-sse")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(WebFluxSseClientTransport.class);

				assertThat(getSseEndpoint((WebFluxSseClientTransport) transports.get(0).transport()))
					.isEqualTo("/custom-sse");
			});
	}

	@Test
	void customWebClientBuilderIsUsed() {
		this.applicationContext.withUserConfiguration(CustomWebClientConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(WebClient.Builder.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	void customJsonMapperIsUsed() {
		this.applicationContext.withUserConfiguration(JsonMapperConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(JsonMapper.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	void defaultSseEndpointIsUsedWhenNotSpecified() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(WebFluxSseClientTransport.class);
				// Default SSE endpoint is "/sse" as specified in the configuration class
			});
	}

	@Test
	void mixedConnectionsWithAndWithoutCustomSseEndpoint() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=/custom-sse",
					"spring.ai.mcp.client.sse.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("sseWebFluxClientTransports", List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof WebFluxSseClientTransport);
				for (NamedClientMcpTransport transport : transports) {
					assertThat(transport.transport()).isInstanceOf(WebFluxSseClientTransport.class);
					if (transport.name().equals("server1")) {
						assertThat(getSseEndpoint((WebFluxSseClientTransport) transport.transport()))
							.isEqualTo("/custom-sse");
					}
					else {
						assertThat(getSseEndpoint((WebFluxSseClientTransport) transport.transport())).isEqualTo("/sse");
					}
				}
			});
	}

	private String getSseEndpoint(WebFluxSseClientTransport transport) {
		Field privateField = ReflectionUtils.findField(WebFluxSseClientTransport.class, "sseEndpoint");
		ReflectionUtils.makeAccessible(privateField);
		return (String) ReflectionUtils.getField(privateField, transport);
	}

	@Configuration
	static class CustomWebClientConfiguration {

		@Bean
		WebClient.Builder webClientBuilder() {
			return WebClient.builder().baseUrl("http://custom-base-url");
		}

	}

	@Configuration
	static class JsonMapperConfiguration {

		@Bean
		JsonMapper jsonMapper() {
			return new JsonMapper();
		}

	}

}
