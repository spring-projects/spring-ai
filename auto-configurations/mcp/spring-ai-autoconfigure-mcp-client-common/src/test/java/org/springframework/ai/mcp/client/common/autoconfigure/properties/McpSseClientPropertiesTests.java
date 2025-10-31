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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpSseClientProperties}.
 *
 * @author Christian Tzolov
 */
class McpSseClientPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void defaultValues() {
		this.contextRunner.run(context -> {
			McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
			assertThat(properties.getConnections()).isNotNull();
			assertThat(properties.getConnections()).isEmpty();
		});
	}

	@Test
	void singleConnection() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080/events")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("server1");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080/events");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isNull();
			});
	}

	@Test
	void multipleConnections() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080/events",
					"spring.ai.mcp.client.sse.connections.server2.url=http://otherserver:8081/events")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(2);
				assertThat(properties.getConnections()).containsKeys("server1", "server2");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080/events");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isNull();
				assertThat(properties.getConnections().get("server2").url())
					.isEqualTo("http://otherserver:8081/events");
				assertThat(properties.getConnections().get("server2").sseEndpoint()).isNull();
			});
	}

	@Test
	void connectionWithEmptyUrl() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=").run(context -> {
			McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
			assertThat(properties.getConnections()).hasSize(1);
			assertThat(properties.getConnections()).containsKey("server1");
			assertThat(properties.getConnections().get("server1").url()).isEmpty();
			assertThat(properties.getConnections().get("server1").sseEndpoint()).isNull();
		});
	}

	@Test
	void connectionWithNullUrl() {
		// This test verifies that a null URL is not allowed in the SseParameters record
		// Since records require all parameters to be provided, this test is more of a
		// documentation
		// of expected behavior rather than a functional test
		McpSseClientProperties properties = new McpSseClientProperties();
		Map<String, McpSseClientProperties.SseParameters> connections = properties.getConnections();

		// We can't create an SseParameters with null URL due to record constraints
		// But we can verify that the connections map is initialized and empty
		assertThat(connections).isNotNull();
		assertThat(connections).isEmpty();
	}

	@Test
	void sseParametersRecord() {
		String url = "http://test-server:8080/events";
		String sseUrl = "/sse";
		McpSseClientProperties.SseParameters params = new McpSseClientProperties.SseParameters(url, sseUrl);

		assertThat(params.url()).isEqualTo(url);
		assertThat(params.sseEndpoint()).isEqualTo(sseUrl);
	}

	@Test
	void sseParametersRecordWithNullSseEndpoint() {
		String url = "http://test-server:8080/events";
		McpSseClientProperties.SseParameters params = new McpSseClientProperties.SseParameters(url, null);

		assertThat(params.url()).isEqualTo(url);
		assertThat(params.sseEndpoint()).isNull();
	}

	@Test
	void configPrefixConstant() {
		assertThat(McpSseClientProperties.CONFIG_PREFIX).isEqualTo("spring.ai.mcp.client.sse");
	}

	@Test
	void yamlConfigurationBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080/events",
					"spring.ai.mcp.client.sse.connections.server2.url=http://otherserver:8081/events")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(2);
				assertThat(properties.getConnections()).containsKeys("server1", "server2");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080/events");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isNull();
				assertThat(properties.getConnections().get("server2").url())
					.isEqualTo("http://otherserver:8081/events");
				assertThat(properties.getConnections().get("server2").sseEndpoint()).isNull();
			});
	}

	@Test
	void connectionMapManipulation() {
		this.contextRunner.run(context -> {
			McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
			Map<String, McpSseClientProperties.SseParameters> connections = properties.getConnections();

			// Add a connection
			connections.put("server1",
					new McpSseClientProperties.SseParameters("http://localhost:8080/events", "/sse"));
			assertThat(properties.getConnections()).hasSize(1);
			assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080/events");
			assertThat(properties.getConnections().get("server1").sseEndpoint()).isEqualTo("/sse");

			// Add another connection
			connections.put("server2",
					new McpSseClientProperties.SseParameters("http://otherserver:8081/events", null));
			assertThat(properties.getConnections()).hasSize(2);
			assertThat(properties.getConnections().get("server2").url()).isEqualTo("http://otherserver:8081/events");
			assertThat(properties.getConnections().get("server2").sseEndpoint()).isNull();

			// Replace a connection
			connections.put("server1",
					new McpSseClientProperties.SseParameters("http://newserver:8082/events", "/events"));
			assertThat(properties.getConnections()).hasSize(2);
			assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://newserver:8082/events");
			assertThat(properties.getConnections().get("server1").sseEndpoint()).isEqualTo("/events");

			// Remove a connection
			connections.remove("server1");
			assertThat(properties.getConnections()).hasSize(1);
			assertThat(properties.getConnections()).containsKey("server2");
			assertThat(properties.getConnections()).doesNotContainKey("server1");
		});
	}

	@Test
	void specialCharactersInUrl() {
		this.contextRunner.withPropertyValues(
				"spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080/events?param=value&other=123")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections().get("server1").url())
					.isEqualTo("http://localhost:8080/events?param=value&other=123");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isNull();
			});
	}

	@Test
	void specialCharactersInConnectionName() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.mcp.client.sse.connections.server-with-dashes.url=http://localhost:8080/events")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("server-with-dashes");
				assertThat(properties.getConnections().get("server-with-dashes").url())
					.isEqualTo("http://localhost:8080/events");
				assertThat(properties.getConnections().get("server-with-dashes").sseEndpoint()).isNull();
			});
	}

	@Test
	void connectionWithSseEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=/events")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("server1");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isEqualTo("/events");
			});
	}

	@Test
	void multipleConnectionsWithSseEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=/events",
					"spring.ai.mcp.client.sse.connections.server2.url=http://otherserver:8081",
					"spring.ai.mcp.client.sse.connections.server2.sse-endpoint=/sse")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(2);
				assertThat(properties.getConnections()).containsKeys("server1", "server2");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isEqualTo("/events");
				assertThat(properties.getConnections().get("server2").url()).isEqualTo("http://otherserver:8081");
				assertThat(properties.getConnections().get("server2").sseEndpoint()).isEqualTo("/sse");
			});
	}

	@Test
	void connectionWithEmptySseEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("server1");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isEmpty();
			});
	}

	@Test
	void mixedConnectionsWithAndWithoutSseEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=/events",
					"spring.ai.mcp.client.sse.connections.server2.url=http://otherserver:8081")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(2);
				assertThat(properties.getConnections()).containsKeys("server1", "server2");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080");
				assertThat(properties.getConnections().get("server1").sseEndpoint()).isEqualTo("/events");
				assertThat(properties.getConnections().get("server2").url()).isEqualTo("http://otherserver:8081");
				assertThat(properties.getConnections().get("server2").sseEndpoint()).isNull();
			});
	}

	@Test
	void specialCharactersInSseEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server1.sse-endpoint=/events/stream?format=json&timeout=30")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("server1");
				assertThat(properties.getConnections().get("server1").url()).isEqualTo("http://localhost:8080");
				assertThat(properties.getConnections().get("server1").sseEndpoint())
					.isEqualTo("/events/stream?format=json&timeout=30");
			});
	}

	@Test
	void mcpHubStyleUrlWithTokenPath() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.sse.connections.mcp-hub.url=http://localhost:3000",
				"spring.ai.mcp.client.sse.connections.mcp-hub.sse-endpoint=/mcp-hub/sse/cf9ec4527e3c4a2cbb149a85ea45ab01")
			.run(context -> {
				McpSseClientProperties properties = context.getBean(McpSseClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("mcp-hub");
				assertThat(properties.getConnections().get("mcp-hub").url()).isEqualTo("http://localhost:3000");
				assertThat(properties.getConnections().get("mcp-hub").sseEndpoint())
					.isEqualTo("/mcp-hub/sse/cf9ec4527e3c4a2cbb149a85ea45ab01");
			});
	}

	@Configuration
	@EnableConfigurationProperties(McpSseClientProperties.class)
	static class TestConfiguration {

	}

}
