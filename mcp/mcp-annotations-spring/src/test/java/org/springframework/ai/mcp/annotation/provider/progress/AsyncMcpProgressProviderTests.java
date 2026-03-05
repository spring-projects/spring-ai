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

package org.springframework.ai.mcp.annotation.provider.progress;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.method.progress.AsyncProgressSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AsyncMcpProgressProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpProgressProviderTests {

	@Test
	void testGetProgressSpecifications() {
		CountDownLatch latch = new CountDownLatch(1);
		AsyncProgressHandler progressHandler = new AsyncProgressHandler(latch);
		AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(List.of(progressHandler));

		List<AsyncProgressSpecification> specifications = provider.getProgressSpecifications();
		List<Function<ProgressNotification, Mono<Void>>> handlers = specifications.stream()
			.map(AsyncProgressSpecification::progressHandler)
			.toList();

		// Should find 2 valid annotated methods (only Mono<Void> methods are valid for
		// async)
		assertThat(handlers).hasSize(2);

		// Test the first handler (Mono<Void> method)
		ProgressNotification notification = new ProgressNotification("test-token-123", 0.5, 100.0,
				"Test progress message");

		StepVerifier.create(handlers.get(0).apply(notification)).verifyComplete();

		try {
			// Wait for progress notifications to be processed
			latch.await(3, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertThat(progressHandler.lastNotification).isEqualTo(notification);

		// Reset
		progressHandler.lastNotification = null;
		progressHandler.lastProgress = null;
		progressHandler.lastProgressToken = null;
		progressHandler.lastTotal = null;

		// Test the second handler (Mono<Void> with params)
		StepVerifier.create(handlers.get(1).apply(notification)).verifyComplete();
		assertThat(progressHandler.lastProgress).isEqualTo(notification.progress());
		assertThat(progressHandler.lastProgressToken).isEqualTo(notification.progressToken());
		assertThat(progressHandler.lastTotal).isEqualTo(String.valueOf(notification.total()));
	}

	@Test
	void testEmptyList() {
		AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(List.of());

		List<Function<ProgressNotification, Mono<Void>>> handlers = provider.getProgressSpecifications()
			.stream()
			.map(AsyncProgressSpecification::progressHandler)
			.toList();

		assertThat(handlers).isEmpty();
	}

	@Test
	void testMultipleObjects() {
		AsyncProgressHandler handler1 = new AsyncProgressHandler();
		AsyncProgressHandler handler2 = new AsyncProgressHandler();
		AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(List.of(handler1, handler2));

		List<Function<ProgressNotification, Mono<Void>>> handlers = provider.getProgressSpecifications()
			.stream()
			.map(AsyncProgressSpecification::progressHandler)
			.toList();

		// Should find 4 valid annotated methods (2 from each handler - only Mono<Void>
		// methods)
		assertThat(handlers).hasSize(4);
	}

	@Test
	void testNullProgressObjects() {
		AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(null);

		List<Function<ProgressNotification, Mono<Void>>> handlers = provider.getProgressSpecifications()
			.stream()
			.map(AsyncProgressSpecification::progressHandler)
			.toList();

		assertThat(handlers).isEmpty();
	}

	@Test
	void testClientIdExtraction() {
		AsyncProgressHandler handler = new AsyncProgressHandler();
		AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(List.of(handler));

		List<AsyncProgressSpecification> specifications = provider.getProgressSpecifications();

		// All specifications should have non-empty client Ids
		assertThat(specifications).allMatch(spec -> spec.clients().length > 0);
	}

	@Test
	void testErrorHandling() {
		// Test class with method that throws an exception
		class ErrorHandler {

			@McpProgress(clients = "my-client-id")
			public Mono<Void> handleProgressWithError(ProgressNotification notification) {
				return Mono.error(new RuntimeException("Test error"));
			}

		}

		ErrorHandler errorHandler = new ErrorHandler();
		AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(List.of(errorHandler));

		List<Function<ProgressNotification, Mono<Void>>> handlers = provider.getProgressSpecifications()
			.stream()
			.map(AsyncProgressSpecification::progressHandler)
			.toList();

		assertThat(handlers).hasSize(1);

		ProgressNotification notification = new ProgressNotification("error-token", 0.5, 100.0, "Error test");

		// Verify that the error is propagated correctly
		StepVerifier.create(handlers.get(0).apply(notification)).expectError(RuntimeException.class).verify();
	}

	/**
	 * Test class with async progress handler methods.
	 */
	static class AsyncProgressHandler {

		final CountDownLatch latch;

		private ProgressNotification lastNotification;

		private Double lastProgress;

		private String lastProgressToken;

		private String lastTotal;

		AsyncProgressHandler(CountDownLatch latch) {
			this.latch = latch;
		}

		AsyncProgressHandler() {
			this.latch = new CountDownLatch(2);
		}

		@McpProgress(clients = "my-client-id")
		public void handleProgressVoid(ProgressNotification notification) {
			this.lastNotification = notification;
		}

		@McpProgress(clients = "my-client-id")
		public Mono<Void> handleProgressMono(ProgressNotification notification) {
			this.lastNotification = notification;
			this.latch.countDown();
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
			this.latch.countDown();
			return Mono.empty();
		}

		@McpProgress(clients = "my-client-id")
		public void handleProgressWithPrimitiveDouble(double progress, String progressToken, String total) {
			this.lastProgress = progress;
			this.lastProgressToken = progressToken;
			this.lastTotal = total;
		}

		// This method is not annotated and should be ignored
		public Mono<Void> notAnnotatedMethod(ProgressNotification notification) {
			// This method should be ignored
			return Mono.empty();
		}

		// This method has invalid return type and should be ignored
		@McpProgress(clients = "my-client-id")
		public String invalidReturnType(ProgressNotification notification) {
			return "Invalid";
		}

		// This method has invalid Mono return type and should be ignored
		@McpProgress(clients = "my-client-id")
		public Mono<String> invalidMonoReturnType(ProgressNotification notification) {
			return Mono.just("Invalid");
		}

	}

}
