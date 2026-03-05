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

package org.springframework.ai.mcp.annotation.method.progress;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpProgress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AsyncMcpProgressMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpProgressMethodCallbackTests {

	// ProgressNotification constructor: (String progressToken, double progress, Double
	// total, String message)
	private static final ProgressNotification TEST_NOTIFICATION = new ProgressNotification("progress-token-123", // progressToken
			0.5, // progress
			100.0, // total
			"Processing..." // message
	);

	@Test
	void testValidVoidMethod() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleProgressVoid", ProgressNotification.class);

		Function<ProgressNotification, Mono<Void>> callback = AsyncMcpProgressMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastNotification).isEqualTo(TEST_NOTIFICATION);
	}

	@Test
	void testValidMethodWithNotification() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleProgressMono", ProgressNotification.class);

		Function<ProgressNotification, Mono<Void>> callback = AsyncMcpProgressMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastNotification).isEqualTo(TEST_NOTIFICATION);
	}

	@Test
	void testValidMethodWithParams() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleProgressWithParams", Double.class, String.class,
				String.class);

		Function<ProgressNotification, Mono<Void>> callback = AsyncMcpProgressMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastProgress).isEqualTo(TEST_NOTIFICATION.progress());
		assertThat(bean.lastProgressToken).isEqualTo(TEST_NOTIFICATION.progressToken());
		assertThat(bean.lastTotal).isEqualTo(String.valueOf(TEST_NOTIFICATION.total()));
	}

	@Test
	void testValidMethodWithParamsMono() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleProgressWithParamsMono", Double.class, String.class,
				String.class);

		Function<ProgressNotification, Mono<Void>> callback = AsyncMcpProgressMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastProgress).isEqualTo(TEST_NOTIFICATION.progress());
		assertThat(bean.lastProgressToken).isEqualTo(TEST_NOTIFICATION.progressToken());
		assertThat(bean.lastTotal).isEqualTo(String.valueOf(TEST_NOTIFICATION.total()));
	}

	@Test
	void testValidMethodWithPrimitiveDouble() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleProgressWithPrimitiveDouble", double.class, String.class,
				String.class);

		Function<ProgressNotification, Mono<Void>> callback = AsyncMcpProgressMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_NOTIFICATION)).verifyComplete();

		assertThat(bean.lastProgress).isEqualTo(TEST_NOTIFICATION.progress());
		assertThat(bean.lastProgressToken).isEqualTo(TEST_NOTIFICATION.progressToken());
		assertThat(bean.lastTotal).isEqualTo(String.valueOf(TEST_NOTIFICATION.total()));
	}

	@Test
	void testInvalidReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidReturnType", ProgressNotification.class);

		assertThatThrownBy(() -> AsyncMcpProgressMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Asynchronous progress methods must return void or Mono<Void>");
	}

	@Test
	void testInvalidMonoReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidMonoReturnType", ProgressNotification.class);

		assertThatThrownBy(() -> AsyncMcpProgressMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Mono return type must be Mono<Void>");
	}

	@Test
	void testInvalidParameterCount() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterCount", ProgressNotification.class,
				String.class);

		assertThatThrownBy(() -> AsyncMcpProgressMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have either 1 parameter (ProgressNotification) or 3 parameters");
	}

	@Test
	void testInvalidParameterType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterType", String.class);

		assertThatThrownBy(() -> AsyncMcpProgressMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Single parameter must be of type ProgressNotification");
	}

	@Test
	void testInvalidParameterTypes() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterTypes", String.class, int.class, boolean.class);

		assertThatThrownBy(() -> AsyncMcpProgressMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("First parameter must be of type Double or double");
	}

	@Test
	void testNullNotification() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleProgressMono", ProgressNotification.class);

		Function<ProgressNotification, Mono<Void>> callback = AsyncMcpProgressMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(null)).expectError(IllegalArgumentException.class).verify();
	}

	/**
	 * Test class with valid methods.
	 */
	static class ValidMethods {

		private ProgressNotification lastNotification;

		private Double lastProgress;

		private String lastProgressToken;

		private String lastTotal;

		@McpProgress(clients = "my-client-id")
		public void handleProgressVoid(ProgressNotification notification) {
			this.lastNotification = notification;
		}

		@McpProgress(clients = "my-client-id")
		public Mono<Void> handleProgressMono(ProgressNotification notification) {
			this.lastNotification = notification;
			return Mono.empty();
		}

		@McpProgress(clients = "my-client-id")
		public void handleProgressWithParams(Double progress, String progressToken, String total) {
			this.lastProgress = progress;
			this.lastProgressToken = progressToken;
			this.lastTotal = total;
		}

		@McpProgress(clients = "my-client-id")
		public Mono<Void> handleProgressWithParamsMono(Double progress, String progressToken, String total) {
			this.lastProgress = progress;
			this.lastProgressToken = progressToken;
			this.lastTotal = total;
			return Mono.empty();
		}

		@McpProgress(clients = "my-client-id")
		public void handleProgressWithPrimitiveDouble(double progress, String progressToken, String total) {
			this.lastProgress = progress;
			this.lastProgressToken = progressToken;
			this.lastTotal = total;
		}

	}

	/**
	 * Test class with invalid methods.
	 */
	static class InvalidMethods {

		@McpProgress(clients = "my-client-id")
		public String invalidReturnType(ProgressNotification notification) {
			return "Invalid";
		}

		@McpProgress(clients = "my-client-id")
		public Mono<String> invalidMonoReturnType(ProgressNotification notification) {
			return Mono.just("Invalid");
		}

		@McpProgress(clients = "my-client-id")
		public void invalidParameterCount(ProgressNotification notification, String extra) {
			// Invalid parameter count
		}

		@McpProgress(clients = "my-client-id")
		public void invalidParameterType(String invalidType) {
			// Invalid parameter type
		}

		@McpProgress(clients = "my-client-id")
		public void invalidParameterTypes(String progress, int progressToken, boolean total) {
			// Invalid parameter types
		}

		@McpProgress(clients = "my-client-id")
		public void invalidFirstParameterType(String progress, String progressToken, String total) {
			// Invalid first parameter type
		}

		@McpProgress(clients = "my-client-id")
		public void invalidSecondParameterType(Double progress, int progressToken, String total) {
			// Invalid second parameter type
		}

		@McpProgress(clients = "my-client-id")
		public void invalidThirdParameterType(Double progress, String progressToken, int total) {
			// Invalid third parameter type
		}

	}

}
