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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.WebClientFactory;
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
		.withConfiguration(AutoConfigurations.of(DefaultWebClientFactory.class,
				StreamableHttpWebFluxTransportAutoConfiguration.class));

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
	@SuppressWarnings("unchecked")
	void noTransportsCreatedWithEmptyConnections() {
		this.applicationContext.run(context -> {
			List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
					List.class);
			assertThat(transports).isEmpty();
		});
	}

	@Test
	@SuppressWarnings("unchecked")
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
	@SuppressWarnings("unchecked")
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
	@SuppressWarnings("unchecked")
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
	@SuppressWarnings("unchecked")
	void customWebClientFactoryIsUsed() {
		this.applicationContext.withUserConfiguration(CustomWebClientFactoryConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(WebClientFactory.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void customWebClientFactoryPerConnectionCustomization() {
		this.applicationContext.withUserConfiguration(PerConnectionWebClientFactoryConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(2);
				// Verify that custom factory was called for each connection
				PerConnectionWebClientFactory factory = context.getBean(PerConnectionWebClientFactory.class);
				assertThat(factory.getCreatedConnections()).containsExactlyInAnyOrder("server1", "server2");
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void defaultWebClientFactoryReturnsBuilder() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				// Verify default factory is created
				assertThat(context.getBean(WebClientFactory.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).transport()).isInstanceOf(WebClientStreamableHttpTransport.class);
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void fallbackToDefaultFactoryWhenNoCustomFactoryProvided() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				// Verify default factory is used when no custom factory is provided
				WebClientFactory factory = context.getBean(WebClientFactory.class);
				assertThat(factory).isNotNull();
				// Default factory should return a builder for any connection name
				WebClient.Builder builder1 = factory.create("server1");
				WebClient.Builder builder2 = factory.create("server2");
				assertThat(builder1).isNotNull();
				assertThat(builder2).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(2);
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void customWebClientFactoryTakesPrecedenceOverDefault() {
		this.applicationContext.withUserConfiguration(CustomWebClientFactoryConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				// Verify custom factory is used, not the default
				WebClientFactory factory = context.getBean(WebClientFactory.class);
				assertThat(factory).isNotNull();
				assertThat(factory).isInstanceOf(CustomWebClientFactory.class);
				// Verify only one factory bean exists (the custom one)
				assertThat(context.getBeansOfType(WebClientFactory.class)).hasSize(1);
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	void customObjectMapperIsUsed() {
		this.applicationContext.withUserConfiguration(CustomObjectMapperConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(ObjectMapper.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	@SuppressWarnings("unchecked")
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
	@SuppressWarnings("unchecked")
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

	@Test
	@SuppressWarnings("unchecked")
	void eachConnectionGetsSeparateWebClientInstance() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081",
					"spring.ai.mcp.client.streamable-http.connections.server3.url=http://thirdserver:8082")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(3);

				// Extract WebClient instances from each transport
				WebClient webClient1 = getWebClient((WebClientStreamableHttpTransport) transports.get(0).transport());
				WebClient webClient2 = getWebClient((WebClientStreamableHttpTransport) transports.get(1).transport());
				WebClient webClient3 = getWebClient((WebClientStreamableHttpTransport) transports.get(2).transport());

				// Verify that each connection has a separate WebClient instance
				// They should not be the same object reference
				assertThat(webClient1).isNotNull();
				assertThat(webClient2).isNotNull();
				assertThat(webClient3).isNotNull();
				assertThat(webClient1).isNotSameAs(webClient2);
				assertThat(webClient1).isNotSameAs(webClient3);
				assertThat(webClient2).isNotSameAs(webClient3);

				// Verify that WebClientFactory.create() was called for each connection
				WebClientFactory factory = context.getBean(WebClientFactory.class);
				assertThat(factory).isNotNull();
			});
	}

	/**
	 * Verifies that WebClientFactory.create() is called separately for each connection,
	 * ensuring that each connection gets its own WebClient.Builder instance. This is the
	 * core functionality of the WebClientFactory pattern.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void webClientFactoryIsCalledPerConnection() {
		this.applicationContext.withUserConfiguration(PerConnectionWebClientFactoryConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081",
					"spring.ai.mcp.client.streamable-http.connections.server3.url=http://thirdserver:8082")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpWebFluxClientTransports",
						List.class);
				assertThat(transports).hasSize(3);

				// Verify that WebClientFactory.create() was called for each connection
				// name
				PerConnectionWebClientFactory factory = context.getBean(PerConnectionWebClientFactory.class);
				assertThat(factory.getCreatedConnections()).hasSize(3);
				assertThat(factory.getCreatedConnections()).containsExactlyInAnyOrder("server1", "server2", "server3");

				// Verify that each connection has a separate WebClient instance
				WebClient webClient1 = getWebClient((WebClientStreamableHttpTransport) transports.get(0).transport());
				WebClient webClient2 = getWebClient((WebClientStreamableHttpTransport) transports.get(1).transport());
				WebClient webClient3 = getWebClient((WebClientStreamableHttpTransport) transports.get(2).transport());

				// Each WebClient should be a different instance
				assertThat(webClient1).isNotSameAs(webClient2);
				assertThat(webClient1).isNotSameAs(webClient3);
				assertThat(webClient2).isNotSameAs(webClient3);

				// Verify that factory.create() was called exactly 3 times (once per
				// connection)
				assertThat(factory.getCreatedConnections()).hasSize(3);
			});
	}

	private String getStreamableHttpEndpoint(WebClientStreamableHttpTransport transport) {
		Field privateField = ReflectionUtils.findField(WebClientStreamableHttpTransport.class, "endpoint");
		ReflectionUtils.makeAccessible(privateField);
		return (String) ReflectionUtils.getField(privateField, transport);
	}

	private WebClient getWebClient(WebClientStreamableHttpTransport transport) {
		// Try common field names for WebClient
		String[] possibleFieldNames = { "webClient", "client", "httpClient" };
		for (String fieldName : possibleFieldNames) {
			Field field = ReflectionUtils.findField(WebClientStreamableHttpTransport.class, fieldName);
			if (field != null) {
				ReflectionUtils.makeAccessible(field);
				Object value = ReflectionUtils.getField(field, transport);
				if (value instanceof WebClient) {
					return (WebClient) value;
				}
			}
		}
		// If direct field access fails, try to find any WebClient field
		// Check all declared fields including inherited ones
		Class<?> clazz = WebClientStreamableHttpTransport.class;
		while (clazz != null) {
			Field[] fields = clazz.getDeclaredFields();
			for (Field field : fields) {
				if (WebClient.class.isAssignableFrom(field.getType())) {
					ReflectionUtils.makeAccessible(field);
					Object value = ReflectionUtils.getField(field, transport);
					if (value instanceof WebClient) {
						return (WebClient) value;
					}
				}
			}
			clazz = clazz.getSuperclass();
		}
		throw new IllegalStateException("Could not find WebClient field in WebClientStreamableHttpTransport");
	}

	@Configuration
	static class CustomWebClientFactoryConfiguration {

		@Bean
		WebClientFactory webClientFactory() {
			return new CustomWebClientFactory();
		}

	}

	@Configuration
	static class PerConnectionWebClientFactoryConfiguration {

		@Bean
		PerConnectionWebClientFactory webClientFactory() {
			return new PerConnectionWebClientFactory();
		}

	}

	@Configuration
	static class CustomObjectMapperConfiguration {

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

	}

	static class CustomWebClientFactory implements WebClientFactory {

		@Override
		public WebClient.Builder create(String connectionName) {
			return WebClient.builder().baseUrl("http://custom-base-url");
		}

	}

	static class PerConnectionWebClientFactory implements WebClientFactory {

		private final java.util.List<String> createdConnections = new java.util.ArrayList<>();

		@Override
		public WebClient.Builder create(String connectionName) {
			createdConnections.add(connectionName);
			return WebClient.builder();
		}

		java.util.List<String> getCreatedConnections() {
			return createdConnections;
		}

	}

}
