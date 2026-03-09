/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.method.logging;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpLogging;

/**
 * Example class demonstrating the use of {@link AsyncMcpLoggingMethodCallback}.
 *
 * This class shows how to create and use an asynchronous logging consumer method
 * callback. It provides examples of methods annotated with {@link McpLogging} that can be
 * used to handle logging message notifications in a reactive way.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpLoggingMethodCallbackExample {

	/**
	 * Example method that accepts a LoggingMessageNotification and returns Mono<Void>.
	 * @param notification The logging message notification
	 * @return A Mono that completes when the processing is done
	 */
	@McpLogging(clients = "test-client")
	public Mono<Void> handleLoggingMessage(LoggingMessageNotification notification) {
		return Mono.fromRunnable(() -> System.out.println("Received logging message: " + notification.level() + " - "
				+ notification.logger() + " - " + notification.data()));
	}

	/**
	 * Example method that accepts individual parameters (LoggingLevel, String, String)
	 * and returns Mono<Void>.
	 * @param level The logging level
	 * @param logger The logger name
	 * @param data The log message data
	 * @return A Mono that completes when the processing is done
	 */
	@McpLogging(clients = "test-client")
	public Mono<Void> handleLoggingMessageWithParams(LoggingLevel level, String logger, String data) {
		return Mono.fromRunnable(() -> System.out
			.println("Received logging message with params: " + level + " - " + logger + " - " + data));
	}

	/**
	 * Example method that accepts a LoggingMessageNotification with void return type.
	 * @param notification The logging message notification
	 */
	@McpLogging(clients = "test-client")
	public void handleLoggingMessageVoid(LoggingMessageNotification notification) {
		System.out.println("Received logging message (void): " + notification.level() + " - " + notification.logger()
				+ " - " + notification.data());
	}

	/**
	 * Example of how to create and use an AsyncMcpLoggingConsumerMethodCallback.
	 * @param args Command line arguments
	 * @throws Exception If an error occurs
	 */
	public static void main(String[] args) throws Exception {
		// Create an instance of the example class
		AsyncMcpLoggingMethodCallbackExample example = new AsyncMcpLoggingMethodCallbackExample();

		// Create a callback for the handleLoggingMessage method
		Method method1 = AsyncMcpLoggingMethodCallbackExample.class.getMethod("handleLoggingMessage",
				LoggingMessageNotification.class);
		Function<LoggingMessageNotification, Mono<Void>> callback1 = AsyncMcpLoggingMethodCallback.builder()
			.method(method1)
			.bean(example)
			.build();

		// Create a callback for the handleLoggingMessageWithParams method
		Method method2 = AsyncMcpLoggingMethodCallbackExample.class.getMethod("handleLoggingMessageWithParams",
				LoggingLevel.class, String.class, String.class);
		Function<LoggingMessageNotification, Mono<Void>> callback2 = AsyncMcpLoggingMethodCallback.builder()
			.method(method2)
			.bean(example)
			.build();

		// Create a callback for the handleLoggingMessageVoid method
		Method method3 = AsyncMcpLoggingMethodCallbackExample.class.getMethod("handleLoggingMessageVoid",
				LoggingMessageNotification.class);
		Function<LoggingMessageNotification, Mono<Void>> callback3 = AsyncMcpLoggingMethodCallback.builder()
			.method(method3)
			.bean(example)
			.build();

		// Create a sample logging message notification
		LoggingMessageNotification notification = new LoggingMessageNotification(LoggingLevel.INFO, "test-logger",
				"This is a test message");

		// Use the callbacks
		System.out.println("Using callback1:");
		callback1.apply(notification).block();

		System.out.println("\nUsing callback2:");
		callback2.apply(notification).block();

		System.out.println("\nUsing callback3 (void method):");
		callback3.apply(notification).block();
	}

}
