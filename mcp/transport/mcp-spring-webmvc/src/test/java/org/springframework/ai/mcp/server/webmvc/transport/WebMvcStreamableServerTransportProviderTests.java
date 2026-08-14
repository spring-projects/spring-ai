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

package org.springframework.ai.mcp.server.webmvc.transport;

import io.modelcontextprotocol.spec.McpStreamableServerSession;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebMvcStreamableServerTransportProvider}.
 *
 * @author Dimitar Proynov
 */
class WebMvcStreamableServerTransportProviderTests {

	private static final String INITIALIZE_REQUEST = """
			{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05",\
			"capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}""";

	@Test
	void internalErrorResponseDoesNotLeakExceptionMessageToClient() {
		String sensitiveDetail = "/opt/app/config/prod-secrets.yaml (No such file or directory)";

		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		provider.setSessionFactory(initializeRequest -> {
			McpStreamableServerSession session = mock(McpStreamableServerSession.class);
			when(session.getId()).thenReturn("test-session");
			// The session initialization fails with an exception whose message embeds
			// sensitive internal detail that must not reach the remote client.
			return new McpStreamableServerSession.McpStreamableServerSessionInit(session,
					Mono.error(new RuntimeException(sensitiveDetail)));
		});

		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectBody(String.class)
			.value(body -> assertThat(body).contains("Internal server error. Check server logs for details.")
				.doesNotContain(sensitiveDetail));
	}

}
