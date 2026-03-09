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

package org.springframework.ai.mcp.annotation.method.logging;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;

import org.springframework.ai.mcp.annotation.McpLogging;

/**
 * Example class demonstrating the use of {@link SyncMcpLoggingMethodCallback}.
 *
 * This class shows how to create and use a synchronous logging consumer method callback.
 * It provides examples of methods annotated with {@link McpLogging} that can be used to
 * handle logging message notifications.
 *
 * @author Christian Tzolov
 */
public class SyncMcpLoggingMethodCallbackExample {

	/**
	 * Example method that accepts a LoggingMessageNotification.
	 * @param notification The logging message notification
	 */
	@McpLogging(clients = "test-client")
	public void handleLoggingMessage(LoggingMessageNotification notification) {
		System.out.println("Received logging message: " + notification.level() + " - " + notification.logger() + " - "
				+ notification.data());
	}

	/**
	 * Example method that accepts individual parameters (LoggingLevel, String, String).
	 * @param level The logging level
	 * @param logger The logger name
	 * @param data The log message data
	 */
	@McpLogging(clients = "test-client")
	public void handleLoggingMessageWithParams(LoggingLevel level, String logger, String data) {
		System.out.println("Received logging message with params: " + level + " - " + logger + " - " + data);
	}

	/**
	 * Example of how to create and use a SyncMcpLoggingConsumerMethodCallback.
	 * @param args Command line arguments
	 * @throws Exception If an error occurs
	 */
	public static void main(String[] args) throws Exception {
		// Create an instance of the example class
		SyncMcpLoggingMethodCallbackExample example = new SyncMcpLoggingMethodCallbackExample();

		// Create a callback for the handleLoggingMessage method
		Method method1 = SyncMcpLoggingMethodCallbackExample.class.getMethod("handleLoggingMessage",
				LoggingMessageNotification.class);
		Consumer<LoggingMessageNotification> callback1 = SyncMcpLoggingMethodCallback.builder()
			.method(method1)
			.bean(example)
			.build();

		// Create a callback for the handleLoggingMessageWithParams method
		Method method2 = SyncMcpLoggingMethodCallbackExample.class.getMethod("handleLoggingMessageWithParams",
				LoggingLevel.class, String.class, String.class);
		Consumer<LoggingMessageNotification> callback2 = SyncMcpLoggingMethodCallback.builder()
			.method(method2)
			.bean(example)
			.build();

		// Create a sample logging message notification
		LoggingMessageNotification notification = new LoggingMessageNotification(LoggingLevel.INFO, "test-logger",
				"This is a test message");

		// Use the callbacks
		System.out.println("Using callback1:");
		callback1.accept(notification);

		System.out.println("\nUsing callback2:");
		callback2.accept(notification);
	}

}
