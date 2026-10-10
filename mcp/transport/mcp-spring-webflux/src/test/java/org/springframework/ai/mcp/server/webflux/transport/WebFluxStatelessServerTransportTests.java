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

package org.springframework.ai.mcp.server.webflux.transport;

import io.modelcontextprotocol.server.McpStatelessServerHandler;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link WebFluxStatelessServerTransport}.
 *
 * @author Taewoong Kim
 */
class WebFluxStatelessServerTransportTests {

	private static final String MALFORMED_REQUEST = """
			{"jsonrpc":"2.0","id":1,"method":"tools/list""";

	@Test
	void malformedJsonReturnsJsonRpcErrorResponse() {
		WebFluxStatelessServerTransport transport = WebFluxStatelessServerTransport.builder().build();
		transport.setMcpHandler(mock(McpStatelessServerHandler.class));

		WebTestClient client = WebTestClient.bindToRouterFunction(transport.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(MALFORMED_REQUEST)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentType(MediaType.APPLICATION_JSON)
			.expectBody(String.class)
			.value(response -> JsonAssertions.assertThatJson(response).isEqualTo("""
					{
						"jsonrpc": "2.0",
						"error": {
							"code": -32600,
							"message": "Invalid message format"
						}
					}
					"""));
	}

}
