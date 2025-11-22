/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.tool;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AsyncToolCallback}.
 *
 * @author Spring AI Team
 * @since 1.2.0
 */
class AsyncToolCallbackTest {

	@Test
	void testCallAsyncReturnsExpectedResult() {
		TestAsyncToolCallback tool = new TestAsyncToolCallback("testTool", "Async result");

		String result = tool.callAsync("{}", null).block();

		assertThat(result).isEqualTo("Async result");
	}

	@Test
	void testCallAsyncWithDelay() {
		TestAsyncToolCallback tool = new TestAsyncToolCallback("testTool", "Delayed result", Duration.ofMillis(100));

		long startTime = System.currentTimeMillis();
		String result = tool.callAsync("{}", null).block();
		long endTime = System.currentTimeMillis();

		assertThat(result).isEqualTo("Delayed result");
		assertThat(endTime - startTime).isGreaterThanOrEqualTo(90); // Allow some margin
	}

	@Test
	void testSupportsAsyncDefaultIsTrue() {
		TestAsyncToolCallback tool = new TestAsyncToolCallback("testTool", "result");

		assertThat(tool.supportsAsync()).isTrue();
	}

	@Test
	void testSupportsAsyncCanBeOverridden() {
		AsyncToolCallback tool = new AsyncToolCallback() {
			@Override
			public Mono<String> callAsync(String toolInput, ToolContext context) {
				return Mono.just("result");
			}

			@Override
			public boolean supportsAsync() {
				return false;
			}

			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder().name("test").inputSchema("{}").build();
			}
		};

		assertThat(tool.supportsAsync()).isFalse();
	}

	@Test
	void testSynchronousFallbackCallBlocksOnAsync() {
		TestAsyncToolCallback tool = new TestAsyncToolCallback("testTool", "Async result");

		String result = tool.call("{}", null);

		assertThat(result).isEqualTo("Async result");
	}

	@Test
	void testSynchronousFallbackWithDelayedAsync() {
		TestAsyncToolCallback tool = new TestAsyncToolCallback("testTool", "Delayed result", Duration.ofMillis(100));

		long startTime = System.currentTimeMillis();
		String result = tool.call("{}", null);
		long endTime = System.currentTimeMillis();

		assertThat(result).isEqualTo("Delayed result");
		assertThat(endTime - startTime).isGreaterThanOrEqualTo(90);
	}

	@Test
	void testAsyncErrorHandling() {
		AsyncToolCallback tool = new AsyncToolCallback() {
			@Override
			public Mono<String> callAsync(String toolInput, ToolContext context) {
				return Mono.error(new ToolExecutionException(getToolDefinition(), new RuntimeException("Async error")));
			}

			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder().name("failingTool").inputSchema("{}").build();
			}
		};

		assertThatThrownBy(() -> tool.callAsync("{}", null).block()).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("Async error");
	}

	@Test
	void testAsyncCallbackWithReturnDirect() {
		TestAsyncToolCallback tool = new TestAsyncToolCallback("directTool", "Direct result", true);

		assertThat(tool.getToolMetadata().returnDirect()).isTrue();

		String result = tool.callAsync("{}", null).block();
		assertThat(result).isEqualTo("Direct result");
	}

	/**
	 * Test implementation of AsyncToolCallback.
	 */
	static class TestAsyncToolCallback implements AsyncToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		private final String result;

		private final Duration delay;

		TestAsyncToolCallback(String name, String result) {
			this(name, result, Duration.ZERO, false);
		}

		TestAsyncToolCallback(String name, String result, boolean returnDirect) {
			this(name, result, Duration.ZERO, returnDirect);
		}

		TestAsyncToolCallback(String name, String result, Duration delay) {
			this(name, result, delay, false);
		}

		TestAsyncToolCallback(String name, String result, Duration delay, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
			this.result = result;
			this.delay = delay;
		}

		@Override
		public Mono<String> callAsync(String toolInput, ToolContext context) {
			if (this.delay.isZero()) {
				return Mono.just(this.result);
			}
			return Mono.just(this.result).delayElement(this.delay);
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

	}

}
