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

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebClientStreamableHttpTransport}.
 *
 * @author Jewoo Shin
 */
class WebClientStreamableHttpTransportTests {

	@Test
	void endpointQueryIsPreservedForPostRequests() {
		AtomicReference<URI> requestedUri = new AtomicReference<>();
		WebClient.Builder builder = WebClient.builder().baseUrl("https://mcp.amap.com").exchangeFunction(request -> {
			requestedUri.set(request.url());
			return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build());
		});

		var transport = WebClientStreamableHttpTransport.builder(builder).endpoint("/mcp?key=test").build();

		StepVerifier.create(transport.connect(Function.identity())).verifyComplete();
		StepVerifier.create(transport.sendMessage(createTestMessage())).verifyComplete();

		assertThat(requestedUri.get()).isEqualTo(URI.create("https://mcp.amap.com/mcp?key=test"));
	}

	private McpSchema.JSONRPCRequest createTestMessage() {
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("Test Client", "1.0.0"));
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "test-id",
				initializeRequest);
	}

}
