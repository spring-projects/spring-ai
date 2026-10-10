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

package org.springframework.ai.mcp.client.webflux.transport;

import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportSessionClosedException;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

@Testcontainers
@Timeout(60)
class WebClientStreamableHttpTransportIT {

	private static final Log logger = LogFactory.getLog(WebClientStreamableHttpTransportIT.class);

	@Container
	@SuppressWarnings("resource")
	static GenericContainer<?> container = new GenericContainer<>("docker.io/node:lts-alpine3.23")
		.withCommand("npx -y @modelcontextprotocol/server-everything@2025.12.18 streamableHttp")
		.withLogConsumer(outputFrame -> logger.info(outputFrame.getUtf8String()))
		.withExposedPorts(3001)
		.waitingFor(Wait.forHttp("/").forStatusCode(404));

	WebClient.Builder builder = WebClient.builder()
		.baseUrl("http://" + container.getHost() + ":" + container.getMappedPort(3001));

	@Test
	void testCloseUninitialized() {
		var transport = WebClientStreamableHttpTransport.builder(this.builder).build();

		StepVerifier.create(transport.closeGracefully()).verifyComplete();

		var initializeRequest = McpSchema.InitializeRequest
			.builder(ProtocolVersions.MCP_2025_06_18, McpSchema.ClientCapabilities.builder().roots(true).build(),
					McpSchema.Implementation.builder("MCP Client", "0.3.1").build())
			.build();
		var testMessage = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE,
				"test-id", initializeRequest);

		StepVerifier.create(transport.sendMessage(testMessage))
			.expectErrorMessage("Transport has already been closed.")
			.verify();
	}

	@Test
	void testCloseInitialized() {
		var transport = WebClientStreamableHttpTransport.builder(this.builder).build();
		transport.connect(Function.identity()).block();

		var initializeRequest = McpSchema.InitializeRequest
			.builder(ProtocolVersions.MCP_2025_06_18, McpSchema.ClientCapabilities.builder().roots(true).build(),
					McpSchema.Implementation.builder("MCP Client", "0.3.1").build())
			.build();
		var testMessage = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE,
				"test-id", initializeRequest);

		StepVerifier.create(transport.sendMessage(testMessage)).verifyComplete();
		StepVerifier.create(transport.closeGracefully()).verifyComplete();

		StepVerifier.create(transport.sendMessage(testMessage))
			.expectErrorMatches(err -> err instanceof McpTransportSessionClosedException
					&& err.getMessage().equals("Transport has already been closed."))
			.verify();
	}

}
