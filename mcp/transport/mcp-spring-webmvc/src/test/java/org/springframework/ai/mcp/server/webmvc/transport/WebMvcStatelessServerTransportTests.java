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

import io.modelcontextprotocol.server.McpStatelessServerHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebMvcStatelessServerTransport}.
 *
 * @author Dimitar Proynov
 */
class WebMvcStatelessServerTransportTests {

	private static final String SENSITIVE_DETAIL = "/opt/app/config/prod-secrets.yaml (No such file or directory)";

	private static final String REQUEST = """
			{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"read","arguments":{}}}""";

	private static final String NOTIFICATION = """
			{"jsonrpc":"2.0","method":"notifications/progress","params":{}}""";

	@Test
	void requestErrorResponseDoesNotLeakExceptionMessageToClient() {
		McpStatelessServerHandler handler = mock(McpStatelessServerHandler.class);
		when(handler.handleRequest(any(), any())).thenReturn(Mono.error(new RuntimeException(SENSITIVE_DETAIL)));

		testMessageDoesntRevealDetails(handler, REQUEST, "Failed to handle request. Check server logs for details.");
	}

	@Test
	void notificationErrorResponseDoesNotLeakExceptionMessageToClient() {
		McpStatelessServerHandler handler = mock(McpStatelessServerHandler.class);
		when(handler.handleNotification(any(), any())).thenReturn(Mono.error(new RuntimeException(SENSITIVE_DETAIL)));

		testMessageDoesntRevealDetails(handler, NOTIFICATION,
				"Failed to handle notification. Check server logs for details.");
	}

	private void testMessageDoesntRevealDetails(McpStatelessServerHandler handler, String body,
			String expectedMessage) {
		WebMvcStatelessServerTransport transport = WebMvcStatelessServerTransport.builder().build();
		transport.setMcpHandler(handler);

		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(transport.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(body)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectBody(String.class)
			.value(response -> assertThat(response).contains(expectedMessage).doesNotContain(SENSITIVE_DETAIL));
	}

}
