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

package org.springframework.ai.mcp.annotation.provider.logging;

import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.method.logging.AsyncLoggingSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AsyncMcpLoggingProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpLoggingProviderTests {

	@Test
	@Disabled
	void testGetLoggingConsumers() {
		TestAsyncLoggingProvider loggingHandler = new TestAsyncLoggingProvider();
		AsyncMcpLoggingProvider provider = new AsyncMcpLoggingProvider(List.of(loggingHandler));

		List<AsyncLoggingSpecification> specifications = provider.getLoggingSpecifications();
		List<Function<LoggingMessageNotification, Mono<Void>>> consumers = specifications.stream()
			.map(AsyncLoggingSpecification::loggingHandler)
			.toList();

		// Should find 3 annotated methods
		assertThat(consumers).hasSize(3);

		// Test the first consumer (Mono return type)
		LoggingMessageNotification notification = new LoggingMessageNotification(LoggingLevel.INFO, "test-logger",
				"This is a test message");

		consumers.get(0).apply(notification).block();

		// Verify that the method was called
		assertThat(loggingHandler.lastNotification).isEqualTo(notification);

		// Reset the state
		loggingHandler.lastNotification = null;

		// Test the second consumer (Mono return type with parameters)
		consumers.get(1).apply(notification).block();

		// Verify that the method was called
		assertThat(loggingHandler.lastLevel).isEqualTo(notification.level());
		assertThat(loggingHandler.lastLogger).isEqualTo(notification.logger());
		assertThat(loggingHandler.lastData).isEqualTo(notification.data());

		// Test the third consumer (void return type)
		consumers.get(2).apply(notification).block();

		// Verify that the method was called
		assertThat(loggingHandler.lastNotification).isEqualTo(notification);
	}

	@Test
	void testEmptyList() {
		AsyncMcpLoggingProvider provider = new AsyncMcpLoggingProvider(List.of());

		List<AsyncLoggingSpecification> specifications = provider.getLoggingSpecifications();

		List<Function<LoggingMessageNotification, Mono<Void>>> consumers = specifications.stream()
			.map(AsyncLoggingSpecification::loggingHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		TestAsyncLoggingProvider handler1 = new TestAsyncLoggingProvider();
		TestAsyncLoggingProvider handler2 = new TestAsyncLoggingProvider();
		AsyncMcpLoggingProvider provider = new AsyncMcpLoggingProvider(List.of(handler1, handler2));

		List<AsyncLoggingSpecification> specifications = provider.getLoggingSpecifications();

		List<Function<LoggingMessageNotification, Mono<Void>>> consumers = specifications.stream()
			.map(AsyncLoggingSpecification::loggingHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler)
		assertThat(consumers).hasSize(4);
	}

	/**
	 * Test class with logging consumer methods.
	 */
	static class TestAsyncLoggingProvider {

		private LoggingMessageNotification lastNotification;

		private LoggingLevel lastLevel;

		private String lastLogger;

		private String lastData;

		@McpLogging(clients = "test-client")
		public Mono<Void> handleLoggingMessage(LoggingMessageNotification notification) {
			return Mono.fromRunnable(() -> this.lastNotification = notification);
		}

		@McpLogging(clients = "test-client")
		public Mono<Void> handleLoggingMessageWithParams(LoggingLevel level, String logger, String data) {
			return Mono.fromRunnable(() -> {
				this.lastLevel = level;
				this.lastLogger = logger;
				this.lastData = data;
			});
		}

		// This should be filtered out since it does not return Mono<Void>
		@McpLogging(clients = "test-client")
		public void handleLoggingMessageVoid(LoggingMessageNotification notification) {
			this.lastNotification = notification;
		}

		// This method is not annotated and should be ignored
		public Mono<Void> notAnnotatedMethod(LoggingMessageNotification notification) {
			return Mono.empty();
		}

	}

}
