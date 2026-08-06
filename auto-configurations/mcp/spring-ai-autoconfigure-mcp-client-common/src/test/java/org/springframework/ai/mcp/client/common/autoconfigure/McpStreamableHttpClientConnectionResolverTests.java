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

package org.springframework.ai.mcp.client.common.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpStreamableHttpClientConnectionResolver}.
 *
 * @author Jewoo Shin
 */
class McpStreamableHttpClientConnectionResolverTests {

	@Test
	void originOnlyUrlUsesDefaultEndpoint() {
		var connection = McpStreamableHttpClientConnectionResolver.resolve("server",
				new ConnectionParameters("https://mcp.example.com", null));

		assertThat(connection.baseUrl()).isEqualTo("https://mcp.example.com");
		assertThat(connection.endpoint()).isEqualTo("/mcp");
	}

	@Test
	void fullUrlWithoutEndpointUsesPathAndQueryAsEndpoint() {
		var connection = McpStreamableHttpClientConnectionResolver.resolve("server",
				new ConnectionParameters("https://mcp.example.com/mcp?key=test", null));

		assertThat(connection.baseUrl()).isEqualTo("https://mcp.example.com");
		assertThat(connection.endpoint()).isEqualTo("/mcp?key=test");
	}

	@Test
	void queryOnlyUrlUsesDefaultPathAndQueryAsEndpoint() {
		var connection = McpStreamableHttpClientConnectionResolver.resolve("server",
				new ConnectionParameters("https://mcp.example.com?key=test", null));

		assertThat(connection.baseUrl()).isEqualTo("https://mcp.example.com");
		assertThat(connection.endpoint()).isEqualTo("/mcp?key=test");
	}

	@Test
	void explicitEndpointWins() {
		var connection = McpStreamableHttpClientConnectionResolver.resolve("server",
				new ConnectionParameters("https://mcp.example.com/base?ignored=true", "/mcp?key=test"));

		assertThat(connection.baseUrl()).isEqualTo("https://mcp.example.com/base?ignored=true");
		assertThat(connection.endpoint()).isEqualTo("/mcp?key=test");
	}

	@Test
	void rawPathAndQueryArePreserved() {
		var connection = McpStreamableHttpClientConnectionResolver.resolve("server",
				new ConnectionParameters("https://mcp.example.com/mcp%20path?key=a%2Fb%20c#fragment", null));

		assertThat(connection.baseUrl()).isEqualTo("https://mcp.example.com");
		assertThat(connection.endpoint()).isEqualTo("/mcp%20path?key=a%2Fb%20c");
	}

}
