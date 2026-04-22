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
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.method.logging.SyncLoggingSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SyncMcpLoggingProvider}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpLoggingProviderTests {

	@Test
	void testGetLoggingConsumers() {
		LoggingHandler loggingHandler = new LoggingHandler();
		SyncMcpLoggingProvider provider = new SyncMcpLoggingProvider(List.of(loggingHandler));

		List<SyncLoggingSpecification> specifications = provider.getLoggingSpecifications();
		List<Consumer<LoggingMessageNotification>> consumers = specifications.stream()
			.map(SyncLoggingSpecification::loggingHandler)
			.toList();

		// Should find 2 annotated methods
		assertThat(consumers).hasSize(2);

		// Test the first consumer
		LoggingMessageNotification notification = new LoggingMessageNotification(LoggingLevel.INFO, "test-logger",
				"This is a test message");
		consumers.get(0).accept(notification);

		// Verify that the method was called
		assertThat(loggingHandler.lastNotification).isEqualTo(notification);

		// Test the second consumer
		consumers.get(1).accept(notification);

		// Verify that the method was called
		assertThat(loggingHandler.lastLevel).isEqualTo(notification.level());
		assertThat(loggingHandler.lastLogger).isEqualTo(notification.logger());
		assertThat(loggingHandler.lastData).isEqualTo(notification.data());
	}

	@Test
	void testEmptyList() {
		SyncMcpLoggingProvider provider = new SyncMcpLoggingProvider(List.of());

		List<Consumer<LoggingMessageNotification>> consumers = provider.getLoggingSpecifications()
			.stream()
			.map(SyncLoggingSpecification::loggingHandler)
			.toList();

		assertThat(consumers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		LoggingHandler handler1 = new LoggingHandler();
		LoggingHandler handler2 = new LoggingHandler();
		SyncMcpLoggingProvider provider = new SyncMcpLoggingProvider(List.of(handler1, handler2));

		List<Consumer<LoggingMessageNotification>> consumers = provider.getLoggingSpecifications()
			.stream()
			.map(SyncLoggingSpecification::loggingHandler)
			.toList();

		// Should find 4 annotated methods (2 from each handler)
		assertThat(consumers).hasSize(4);
	}

	/**
	 * Test class with logging consumer methods.
	 */
	static class LoggingHandler {

		private LoggingMessageNotification lastNotification;

		private LoggingLevel lastLevel;

		private String lastLogger;

		private String lastData;

		@McpLogging(clients = "test-client")
		public void handleLoggingMessage(LoggingMessageNotification notification) {
			System.out.println("1");
			this.lastNotification = notification;
		}

		@McpLogging(clients = "test-client")
		public void handleLoggingMessageWithParams(LoggingLevel level, String logger, String data) {
			System.out.println("2");
			this.lastLevel = level;
			this.lastLogger = logger;
			this.lastData = data;
		}

		// This method is not annotated and should be ignored
		public void notAnnotatedMethod(LoggingMessageNotification notification) {
			// This method should be ignored
		}

	}

}
