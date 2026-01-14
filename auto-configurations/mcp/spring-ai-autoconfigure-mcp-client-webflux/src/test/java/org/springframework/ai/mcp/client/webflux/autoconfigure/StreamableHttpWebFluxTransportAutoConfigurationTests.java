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

import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
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
 * Tests for {@link StreamableHttpWebFluxTransportAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
public class StreamableHttpWebFluxTransportAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(StreamableHttpWebFluxTransportAutoConfiguration.class));

	@Test
	void webFluxClientTransportsPresentIfWebClientStreamableHttpTransportPresent() {
		this.applicationContext
			.run(context -> assertThat(context.containsBean("streamableHttpWebFluxClientTransports")).isTrue());
	}

	@Test
	void webFluxClientTransportsNotPresentIfMissingWebClientStreamableHttpTransportNotPresent() {
		this.applicationContext
			.withClassLoader(new FilteredClassLoader(
					"io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport"))
			.run(context -> assertThat(context.containsBean("streamableHttpWebFluxClientTransports")).isFalse());
	}

	@Test
	void webFluxClientTransportsNotPresentIfMcpClientDisabled() {
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.enabled", "false")
			.run(context -> assertThat(context.containsBean("streamableHttpWebFluxClientTransports")).isFalse());
	}

	@Test
	void noTransportsCreatedWithEmptyConnections() {
		this.applicationContext.run(context -> {
			List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
					List.class);
			assertThat(transports).isEmpty();
		});
	}

	@Test
	void singleConnectionCreatesOneTransport() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(WebClientStreamableHttpTransport.class);
			});
	}

	@Test
	void multipleConnectionsCreateMultipleTransports() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof WebClientStreamableHttpTransport);
				for (NamedClientMcpTransport transport : transports) {
					assertThat(transport.transport()).isInstanceOf(WebClientStreamableHttpTransport.class);
					assertThat(getStreamableHttpEndpoint((WebClientStreamableHttpTransport) transport.transport()))
						.isEqualTo("/mcp");
				}
			});
	}

	@Test
	void customStreamableHttpEndpointIsRespected() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server1.endpoint=/custom-mcp")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(WebClientStreamableHttpTransport.class);

				assertThat(getStreamableHttpEndpoint((WebClientStreamableHttpTransport) transports.get(0).transport()))
					.isEqualTo("/custom-mcp");
			});
	}

	@Test
	void customWebClientBuilderIsUsed() {
		this.applicationContext.withUserConfiguration(CustomWebClientConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(WebClient.Builder.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	void customJsonMapperIsUsed() {
		this.applicationContext.withUserConfiguration(CustomJsonMapperConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(JsonMapper.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	void defaultStreamableHttpEndpointIsUsedWhenNotSpecified() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(WebClientStreamableHttpTransport.class);
				// Default streamable HTTP endpoint is "/mcp" as specified in the
				// configuration class
			});
	}

	@Test
	void mixedConnectionsWithAndWithoutCustomStreamableHttpEndpoint() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server1.endpoint=/custom-mcp",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof WebClientStreamableHttpTransport);
				for (NamedClientMcpTransport transport : transports) {
					assertThat(transport.transport()).isInstanceOf(WebClientStreamableHttpTransport.class);
					if (transport.name().equals("server1")) {
						assertThat(getStreamableHttpEndpoint((WebClientStreamableHttpTransport) transport.transport()))
							.isEqualTo("/custom-mcp");
					}
					else {
						assertThat(getStreamableHttpEndpoint((WebClientStreamableHttpTransport) transport.transport()))
							.isEqualTo("/mcp");
					}
				}
			});
	}

	private String getStreamableHttpEndpoint(WebClientStreamableHttpTransport transport) {
		Field privateField = ReflectionUtils.findField(WebClientStreamableHttpTransport.class, "endpoint");
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
	static class CustomJsonMapperConfiguration {

		@Bean
		JsonMapper jsonMapper() {
			return new JsonMapper();
		}

	}

}
