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

package org.springframework.ai.mcp.client;

import java.time.Duration;

import io.modelcontextprotocol.client.AbstractMcpSyncClientTests;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Tests for the {@link McpSyncClient} with {@link WebFluxSseClientTransport}.
 *
 * @author Christian Tzolov
 */
@Testcontainers
@Timeout(60)
class WebFluxSseMcpSyncClientIT extends AbstractMcpSyncClientTests {

	private static final Log logger = LogFactory.getLog(WebFluxSseMcpSyncClientIT.class);

	@Container
	@SuppressWarnings("resource")
	static GenericContainer<?> container = new GenericContainer<>("docker.io/node:lts-alpine3.23")
		.withCommand("npx -y @modelcontextprotocol/server-everything@2025.12.18 sse")
		.withLogConsumer(outputFrame -> logger.info(outputFrame.getUtf8String()))
		.withExposedPorts(3001)
		.waitingFor(Wait.forHttp("/").forStatusCode(404));

	@Override
	protected McpClientTransport createMcpTransport() {
		String host = "http://" + container.getHost() + ":" + container.getMappedPort(3001);
		return WebFluxSseClientTransport.builder(WebClient.builder().baseUrl(host)).build();
	}

	protected Duration getInitializationTimeout() {
		return Duration.ofSeconds(10);
	}

}
