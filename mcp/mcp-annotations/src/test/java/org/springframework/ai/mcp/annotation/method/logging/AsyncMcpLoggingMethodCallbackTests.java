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
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpLogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AsyncMcpLoggingMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpLoggingMethodCallbackTests {

	private static final LoggingMessageNotification TEST_NOTIFICATION = new LoggingMessageNotification(
			LoggingLevel.INFO, "test-logger", "This is a test message");

	@Test
	void testValidMethodWithNotification() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleLoggingMessage", LoggingMessageNotification.class);

		Function<LoggingMessageNotification, Mono<Void>> callback = AsyncMcpLoggingMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastNotification).isEqualTo(TEST_NOTIFICATION);
	}

	@Test
	void testValidMethodWithParams() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleLoggingMessageWithParams", LoggingLevel.class, String.class,
				String.class);

		Function<LoggingMessageNotification, Mono<Void>> callback = AsyncMcpLoggingMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastLevel).isEqualTo(TEST_NOTIFICATION.level());
		assertThat(bean.lastLogger).isEqualTo(TEST_NOTIFICATION.logger());
		assertThat(bean.lastData).isEqualTo(TEST_NOTIFICATION.data());
	}

	@Test
	void testValidVoidMethod() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleLoggingMessageVoid", LoggingMessageNotification.class);

		Function<LoggingMessageNotification, Mono<Void>> callback = AsyncMcpLoggingMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastNotification).isEqualTo(TEST_NOTIFICATION);
	}

	@Test
	void testInvalidReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidReturnType", LoggingMessageNotification.class);

		assertThatThrownBy(() -> AsyncMcpLoggingMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have void or Mono<Void> return type");
	}

	@Test
	void testInvalidMonoReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidMonoReturnType", LoggingMessageNotification.class);

		// This will pass validation since we can't check the generic type at runtime
		Function<LoggingMessageNotification, Mono<Void>> callback = AsyncMcpLoggingMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		// But it will fail at runtime when we try to cast the result
		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyError(ClassCastException.class);
	}

	@Test
	void testInvalidParameterCount() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterCount", LoggingMessageNotification.class,
				String.class);

		assertThatThrownBy(() -> AsyncMcpLoggingMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have either 1 parameter (LoggingMessageNotification) or 3 parameters");
	}

	@Test
	void testInvalidParameterType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterType", String.class);

		assertThatThrownBy(() -> AsyncMcpLoggingMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Single parameter must be of type LoggingMessageNotification");
	}

	@Test
	void testInvalidParameterTypes() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterTypes", String.class, int.class, boolean.class);

		assertThatThrownBy(() -> AsyncMcpLoggingMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("First parameter must be of type LoggingLevel");
	}

	@Test
	void testNullNotification() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleLoggingMessage", LoggingMessageNotification.class);

		Function<LoggingMessageNotification, Mono<Void>> callback = AsyncMcpLoggingMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(null))
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Notification must not be null"));
	}

	/**
	 * Test class with valid methods.
	 */
	static class ValidMethods {

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

		@McpLogging(clients = "test-client")
		public void handleLoggingMessageVoid(LoggingMessageNotification notification) {
			this.lastNotification = notification;
		}

	}

	/**
	 * Test class with invalid methods.
	 */
	static class InvalidMethods {

		@McpLogging(clients = "test-client")
		public String invalidReturnType(LoggingMessageNotification notification) {
			return "Invalid";
		}

		@McpLogging(clients = "test-client")
		public Mono<String> invalidMonoReturnType(LoggingMessageNotification notification) {
			return Mono.just("Invalid");
		}

		@McpLogging(clients = "test-client")
		public Mono<Void> invalidParameterCount(LoggingMessageNotification notification, String extra) {
			return Mono.empty();
		}

		@McpLogging(clients = "test-client")
		public Mono<Void> invalidParameterType(String invalidType) {
			return Mono.empty();
		}

		@McpLogging(clients = "test-client")
		public Mono<Void> invalidParameterTypes(String level, int logger, boolean data) {
			return Mono.empty();
		}

	}

}
