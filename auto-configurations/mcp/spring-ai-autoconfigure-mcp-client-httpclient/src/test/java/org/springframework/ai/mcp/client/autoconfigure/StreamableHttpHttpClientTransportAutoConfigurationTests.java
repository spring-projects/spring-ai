/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.client.autoconfigure;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StreamableHttpHttpClientTransportAutoConfiguration}.
 *
 * @author Yanming Zhou
 */
public class StreamableHttpHttpClientTransportAutoConfigurationTests {

	private final ApplicationContextRunner applicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(StreamableHttpHttpClientTransportAutoConfiguration.class));

	@Test
	void mcpHttpClientTransportsNotPresentIfMcpClientDisabled() {
		this.applicationContext.withPropertyValues("spring.ai.mcp.client.enabled", "false")
			.run(context -> assertThat(context.containsBean("streamableHttpHttpClientTransports")).isFalse());
	}

	@Test
	void noTransportsCreatedWithEmptyConnections() {
		this.applicationContext.run(context -> {
			List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
					List.class);
			assertThat(transports).isEmpty();
		});
	}

	@Test
	void singleConnectionCreatesOneTransport() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(HttpClientStreamableHttpTransport.class);
			});
	}

	@Test
	void multipleConnectionsCreateMultipleTransports() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof HttpClientStreamableHttpTransport);
				for (NamedClientMcpTransport transport : transports) {
					assertThat(transport.transport()).isInstanceOf(HttpClientStreamableHttpTransport.class);
					assertThat(getStreamableHttpEndpoint((HttpClientStreamableHttpTransport) transport.transport()))
						.isEqualTo("/mcp");
				}
			});
	}

	@Test
	void customEndpointIsRespected() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server1.endpoint=/custom-mcp")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(HttpClientStreamableHttpTransport.class);

				assertThat(getStreamableHttpEndpoint((HttpClientStreamableHttpTransport) transports.get(0).transport()))
					.isEqualTo("/custom-mcp");
			});
	}

	@Test
	void customJsonMapperIsUsed() {
		this.applicationContext.withUserConfiguration(CustomJsonMapperConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean(JsonMapper.class)).isNotNull();
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
			});
	}

	@Test
	void defaultEndpointIsUsedWhenNotSpecified() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).name()).isEqualTo("server1");
				assertThat(transports.get(0).transport()).isInstanceOf(HttpClientStreamableHttpTransport.class);
				// Default Streamable HTTP endpoint is "/mcp" as specified in the
				// configuration class
			});
	}

	@Test
	void mixedConnectionsWithAndWithoutCustomEndpoint() {
		this.applicationContext
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server1.endpoint=/custom-mcp",
					"spring.ai.mcp.client.streamable-http.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(transports).hasSize(2);
				assertThat(transports).extracting("name").containsExactlyInAnyOrder("server1", "server2");
				assertThat(transports).extracting("transport")
					.allMatch(transport -> transport instanceof HttpClientStreamableHttpTransport);
				for (NamedClientMcpTransport transport : transports) {
					assertThat(transport.transport()).isInstanceOf(HttpClientStreamableHttpTransport.class);
					if (transport.name().equals("server1")) {
						assertThat(getStreamableHttpEndpoint((HttpClientStreamableHttpTransport) transport.transport()))
							.isEqualTo("/custom-mcp");
					}
					else {
						assertThat(getStreamableHttpEndpoint((HttpClientStreamableHttpTransport) transport.transport()))
							.isEqualTo("/mcp");
					}
				}
			});
	}

	@Test
	void asyncHttpRequestCustomizerBeanIsApplied() {
		this.applicationContext.withUserConfiguration(AsyncRequestCustomizerConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(headersAppliedBy((HttpClientStreamableHttpTransport) transports.get(0).transport()))
					.containsEntry("X-Async", "applied");
			});
	}

	@Test
	void syncHttpRequestCustomizerBeanIsApplied() {
		this.applicationContext.withUserConfiguration(SyncRequestCustomizerConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(headersAppliedBy((HttpClientStreamableHttpTransport) transports.get(0).transport()))
					.containsEntry("X-Sync", "applied");
			});
	}

	@Test
	void multipleRequestCustomizerBeansAreAllApplied() {
		this.applicationContext
			.withUserConfiguration(AsyncRequestCustomizerConfiguration.class, SyncRequestCustomizerConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(headersAppliedBy((HttpClientStreamableHttpTransport) transports.get(0).transport()))
					.containsEntry("X-Async", "applied")
					.containsEntry("X-Sync", "applied");
			});
	}

	@Test
	void requestCustomizerBeansAreAppliedInOrderAnnotationOrder() {
		this.applicationContext.withUserConfiguration(OrderedRequestCustomizerConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(headersAppliedBy((HttpClientStreamableHttpTransport) transports.get(0).transport()))
					.containsEntry("X-Order", "first,second");
			});
	}

	@Test
	void transportCustomizerStillWinsOverRequestCustomizerBeans() {
		this.applicationContext
			.withUserConfiguration(AsyncRequestCustomizerConfiguration.class, TransportCustomizerConfiguration.class)
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8080")
			.run(context -> {
				List<NamedClientMcpTransport> transports = context.getBean("streamableHttpHttpClientTransports",
						List.class);
				assertThat(headersAppliedBy((HttpClientStreamableHttpTransport) transports.get(0).transport()))
					.containsEntry("X-Transport", "applied")
					.doesNotContainKey("X-Async");
			});
	}

	/**
	 * Invokes the transport's request customizer against a throwaway request builder and
	 * returns the headers it set. The transport exposes no getter for the customizer, so
	 * the field is read reflectively, in the same way as
	 * {@link #getStreamableHttpEndpoint(HttpClientStreamableHttpTransport)}.
	 */
	private Map<String, String> headersAppliedBy(HttpClientStreamableHttpTransport transport) {
		Field privateField = ReflectionUtils.findField(HttpClientStreamableHttpTransport.class,
				"httpRequestCustomizer");
		ReflectionUtils.makeAccessible(privateField);
		McpAsyncHttpClientRequestCustomizer customizer = (McpAsyncHttpClientRequestCustomizer) ReflectionUtils
			.getField(privateField, transport);

		URI endpoint = URI.create("http://localhost:8080/mcp");
		HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint);
		HttpRequest.Builder customized = Mono
			.from(customizer.customize(builder, "POST", endpoint, "{}", McpTransportContext.EMPTY))
			.block();

		Map<String, String> headers = new LinkedHashMap<>();
		customized.build().headers().map().forEach((name, values) -> headers.put(name, String.join(",", values)));
		return headers;
	}

	private String getStreamableHttpEndpoint(HttpClientStreamableHttpTransport transport) {
		Field privateField = ReflectionUtils.findField(HttpClientStreamableHttpTransport.class, "endpoint");
		ReflectionUtils.makeAccessible(privateField);
		return (String) ReflectionUtils.getField(privateField, transport);
	}

	@Configuration
	static class CustomJsonMapperConfiguration {

		@Bean
		JsonMapper jsonMapper() {
			return new JsonMapper();
		}

	}

	@Configuration
	static class AsyncRequestCustomizerConfiguration {

		@Bean
		McpAsyncHttpClientRequestCustomizer asyncRequestCustomizer() {
			return (builder, method, endpoint, body, context) -> {
				builder.setHeader("X-Async", "applied");
				return Mono.just(builder);
			};
		}

	}

	@Configuration
	static class SyncRequestCustomizerConfiguration {

		@Bean
		McpSyncHttpClientRequestCustomizer syncRequestCustomizer() {
			return (builder, method, endpoint, body, context) -> builder.setHeader("X-Sync", "applied");
		}

	}

	@Configuration
	static class OrderedRequestCustomizerConfiguration {

		@Bean
		@Order(2)
		McpAsyncHttpClientRequestCustomizer secondRequestCustomizer() {
			return (builder, method, endpoint, body, context) -> {
				builder.setHeader("X-Order", currentOrderHeader(builder) + ",second");
				return Mono.just(builder);
			};
		}

		@Bean
		@Order(1)
		McpAsyncHttpClientRequestCustomizer firstRequestCustomizer() {
			return (builder, method, endpoint, body, context) -> {
				builder.setHeader("X-Order", "first");
				return Mono.just(builder);
			};
		}

		private static String currentOrderHeader(HttpRequest.Builder builder) {
			return builder.copy().build().headers().firstValue("X-Order").orElse("none");
		}

	}

	@Configuration
	static class TransportCustomizerConfiguration {

		@Bean
		McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> transportCustomizer() {
			return (name, builder) -> builder.asyncHttpRequestCustomizer((request, method, endpoint, body, context) -> {
				request.setHeader("X-Transport", "applied");
				return Mono.just(request);
			});
		}

	}

}
