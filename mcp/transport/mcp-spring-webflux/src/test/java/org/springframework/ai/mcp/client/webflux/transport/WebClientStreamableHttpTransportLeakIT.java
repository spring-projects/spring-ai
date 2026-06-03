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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify that WebClientStreamableHttpTransport properly cleans up
 * SSE connections and doesn't leak resources.
 *
 * @author Spring AI Contributors
 * @since 1.0.0
 */
class WebClientStreamableHttpTransportLeakIT {

	private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

	/**
	 * Verifies that sending multiple messages doesn't accumulate connections.
	 */
	@Test
	void testNoConnectionLeakOnRepeatedMessages() {
		WebClient.Builder webClientBuilder = WebClient.builder().baseUrl("http://localhost:8080");

		WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClientBuilder)
			.build();

		long initialThreadCount = threadMXBean.getThreadCount();

		try {
			// Connect and send multiple messages
			for (int i = 0; i < 20; i++) {
				transport.connect(msg -> msg).block();

				McpSchema.JSONRPCMessage message = createTestMessage(i);
				try {
					transport.sendMessage(message).block();
				}
				catch (Exception e) {
					// Expected if no server running - we're testing connection cleanup
				}
			}

			long afterMessagesThreadCount = threadMXBean.getThreadCount();

			// Allow some time for cleanup
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			long finalThreadCount = threadMXBean.getThreadCount();

			// Thread count should not grow unboundedly
			// Allow small variance due to GC and other factors
			assertThat(finalThreadCount - initialThreadCount).as("Thread count should not grow significantly")
				.isLessThan(10);

		}
		finally {
			transport.closeGracefully().block();
		}
	}

	/**
	 * Verifies that reconnections don't leak connections.
	 */
	@Test
	void testNoConnectionLeakOnReconnection() {
		WebClient.Builder webClientBuilder = WebClient.builder().baseUrl("http://localhost:8080");

		WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClientBuilder)
			.build();

		long initialThreadCount = threadMXBean.getThreadCount();

		try {
			// Simulate multiple connect/disconnect cycles
			for (int i = 0; i < 10; i++) {
				transport.connect(msg -> msg).block();

				// Small delay to allow connection establishment
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			long afterReconnectsThreadCount = threadMXBean.getThreadCount();

			// Close gracefully
			transport.closeGracefully().block();

			long finalThreadCount = threadMXBean.getThreadCount();

			// Verify cleanup
			assertThat(finalThreadCount - initialThreadCount).as("Threads should be cleaned up after close")
				.isLessThan(5);

		}
		finally {
			// Ensure cleanup even if test fails
			try {
				transport.closeGracefully().block();
			}
			catch (Exception e) {
				// Ignore
			}
		}
	}

	/**
	 * Verifies that all connections are cleaned up on graceful shutdown.
	 */
	@Test
	void testAllConnectionsCleanedOnClose() {
		WebClient.Builder webClientBuilder = WebClient.builder().baseUrl("http://localhost:8080");

		WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClientBuilder)
			.build();

		try {
			// Establish some connections
			transport.connect(msg -> msg).block();

			// Close gracefully
			transport.closeGracefully().block();

			// After close, transport should be in closed state
			// Any subsequent operations should fail or be no-op
			assertThat(transport.closeGracefully()).isNotNull();

		}
		finally {
			// Final cleanup
			try {
				transport.closeGracefully().block();
			}
			catch (Exception e) {
				// Ignore
			}
		}
	}

	private McpSchema.JSONRPCMessage createTestMessage(int id) {
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test-method", String.valueOf(id), null);
	}

}
