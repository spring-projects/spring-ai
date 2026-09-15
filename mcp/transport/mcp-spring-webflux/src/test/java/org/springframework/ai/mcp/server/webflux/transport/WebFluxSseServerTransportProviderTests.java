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

import java.util.Map;

import io.modelcontextprotocol.spec.McpServerSession;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebFluxSseServerTransportProvider}.
 *
 * @author Dimitar Proynov
 */
class WebFluxSseServerTransportProviderTests {

	private static final String TOOL_CALL_REQUEST = """
			{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"read","arguments":{}}}""";

	@Test
	@SuppressWarnings("unchecked")
	void internalErrorResponseDoesNotLeakExceptionMessageToClient() {
		String sensitiveDetail = "/opt/app/config/prod-secrets.yaml (No such file or directory)";

		// A registered handler throws an exception whose message embeds sensitive
		// internal detail that must not reach the remote client.
		McpServerSession session = mock(McpServerSession.class);
		when(session.handle(any())).thenReturn(Mono.error(new RuntimeException(sensitiveDetail)));

		WebFluxSseServerTransportProvider provider = WebFluxSseServerTransportProvider.builder().build();
		Map<String, McpServerSession> sessions = (Map<String, McpServerSession>) ReflectionTestUtils.getField(provider,
				"sessions");
		sessions.put("test-session", session);

		WebTestClient client = WebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri(uriBuilder -> uriBuilder.path("/mcp/message").queryParam("sessionId", "test-session").build())
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(TOOL_CALL_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectBody(String.class)
			.value(body -> assertThat(body).contains("Internal server error. Check server logs for details.")
				.doesNotContain(sensitiveDetail));
	}

}
