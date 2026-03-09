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

package org.springframework.ai.mcp.annotation.method.complete;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AsyncMcpCompleteMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpCompleteMethodCallbackTests {

	// Helper method to create a mock McpComplete annotation
	private McpComplete createMockMcpComplete(String prompt, String uri) {
		return new McpComplete() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return McpComplete.class;
			}

			@Override
			public String prompt() {
				return prompt;
			}

			@Override
			public String uri() {
				return uri;
			}
		};
	}

	@Test
	public void testCallbackWithRequestParameter() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion for value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithExchangeAndRequestParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithExchange",
				McpAsyncServerExchange.class, CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion with exchange for value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithArgumentParameter() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithArgument",
				CompleteRequest.CompleteArgument.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion from argument: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithValueParameter() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithValue", String.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion from value: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithPromptAnnotation() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithPrompt", CompleteRequest.class);
		McpComplete completeAnnotation = method.getAnnotation(McpComplete.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.complete(completeAnnotation)
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion for prompt with: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithUriAnnotation() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithUri", CompleteRequest.class);
		McpComplete completeAnnotation = method.getAnnotation(McpComplete.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.complete(completeAnnotation)
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new ResourceReference("test://value"),
				new CompleteRequest.CompleteArgument("variable", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion for URI with: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithCompletionObject() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionObject", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion object for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithCompletionList() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionList", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(2);
			assertThat(result.completion().values().get(0)).isEqualTo("Async list item 1 for: value");
			assertThat(result.completion().values().get(1)).isEqualTo("Async list item 2 for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithCompletionString() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionString", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async string completion for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionResult() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getDirectCompletionResult", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct completion for value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionObject() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getDirectCompletionObject", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct completion object for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionList() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getDirectCompletionList", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(2);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct list item 1 for: value");
			assertThat(result.completion().values().get(1)).isEqualTo("Direct list item 2 for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionString() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getDirectCompletionString", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct string completion for: value");
		}).verifyComplete();
	}

	@Test
	public void testInvalidReturnType() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("invalidReturnType", CompleteRequest.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Method must return either CompleteResult, CompleteCompletion, List<String>, String, or Mono<T>");
	}

	@Test
	public void testInvalidParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("invalidParameters", int.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method parameters must be exchange, CompleteRequest, CompleteArgument, or String");
	}

	@Test
	public void testTooManyParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("tooManyParameters", McpAsyncServerExchange.class,
				CompleteRequest.class, String.class, String.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method can have at most 3 input parameters");
	}

	@Test
	public void testInvalidParameterType() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("invalidParameterType", Object.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method parameters must be exchange, CompleteRequest, CompleteArgument, or String");
	}

	@Test
	public void testDuplicateExchangeParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("duplicateExchangeParameters",
				McpAsyncServerExchange.class, McpAsyncServerExchange.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one exchange parameter");
	}

	@Test
	public void testDuplicateRequestParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("duplicateRequestParameters", CompleteRequest.class,
				CompleteRequest.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one CompleteRequest parameter");
	}

	@Test
	public void testDuplicateArgumentParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("duplicateArgumentParameters",
				CompleteRequest.CompleteArgument.class, CompleteRequest.CompleteArgument.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one CompleteArgument parameter");
	}

	@Test
	public void testMissingPromptAndUri() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Either prompt or uri must be provided");
	}

	@Test
	public void testBothPromptAndUri() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.uri("test://resource")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Only one of prompt or uri can be provided");
	}

	@Test
	public void testNullRequest() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);

		StepVerifier.create(callback.apply(exchange, null))
			.expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
					&& throwable.getMessage().contains("Request must not be null"))
			.verify();
	}

	@Test
	public void testCallbackWithProgressToken() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithProgressToken", String.class,
				CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			// Since CompleteRequest doesn't have progressToken, it should be null
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async completion with progress (no token) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMixedAndProgressToken() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithMixedAndProgress",
				McpAsyncServerExchange.class, String.class, String.class, CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			// Since CompleteRequest doesn't have progressToken, it should be null
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async mixed completion (no token) with value: value and request: value");
		}).verifyComplete();
	}

	@Test
	public void testDuplicateProgressTokenParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("duplicateProgressTokenParameters", String.class,
				String.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one @McpProgressToken parameter");
	}

	@Test
	public void testCallbackWithMeta() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithMeta", McpMeta.class,
				CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"), java.util.Map.of("key", "test-value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async completion with meta (meta: test-value) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaNull() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithMeta", McpMeta.class,
				CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async completion with meta (no meta) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaAndMixed() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithMetaAndMixed",
				McpAsyncServerExchange.class, McpMeta.class, String.class, CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"), java.util.Map.of("key", "test-value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async mixed completion (meta: test-value) with value: value and request: value");
		}).verifyComplete();
	}

	@Test
	public void testDuplicateMetaParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("duplicateMetaParameters", McpMeta.class,
				McpMeta.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one McpMeta parameter");
	}

	@Test
	public void testCallbackWithAsyncRequestContext() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithAsyncRequestContext",
				McpAsyncRequestContext.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async completion with async context for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithAsyncRequestContextAndValue() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithAsyncRequestContextAndValue",
				McpAsyncRequestContext.class, String.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async completion with async context and value: value for: value");
		}).verifyComplete();
	}

	@Test
	public void testDuplicateAsyncRequestContextParameters() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("duplicateAsyncRequestContextParameters",
				McpAsyncRequestContext.class, McpAsyncRequestContext.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one request context parameter");
	}

	@Test
	public void testInvalidSyncRequestContextInAsyncMethod() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("invalidSyncRequestContextInAsyncMethod",
				McpSyncRequestContext.class);

		assertThatThrownBy(() -> AsyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Async complete methods should use McpAsyncRequestContext instead of McpSyncRequestContext parameter");
	}

	@Test
	public void testCallbackWithProgressTokenNonNull() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithProgressToken", String.class,
				CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		// Create a CompleteRequest with progressToken using a mock
		CompleteRequest request = mock(CompleteRequest.class);
		when(request.ref()).thenReturn(new PromptReference("test-prompt"));
		when(request.argument()).thenReturn(new CompleteRequest.CompleteArgument("test", "value"));
		when(request.progressToken()).thenReturn("progress-123");

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async completion with progress (token: progress-123) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithTransportContextParameter() throws Exception {
		TestAsyncCompleteProvider provider = new TestAsyncCompleteProvider();
		Method method = TestAsyncCompleteProvider.class.getMethod("getCompletionWithTransportContext",
				McpTransportContext.class, CompleteRequest.class);

		BiFunction<McpAsyncServerExchange, CompleteRequest, Mono<CompleteResult>> callback = AsyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext transportContext = mock(McpTransportContext.class);
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		when(exchange.transportContext()).thenReturn(transportContext);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async completion with transport context for value");
		}).verifyComplete();
	}

	private static class TestAsyncCompleteProvider {

		public Mono<CompleteResult> getCompletionWithRequest(CompleteRequest request) {
			return Mono.just(new CompleteResult(
					new CompleteCompletion(List.of("Async completion for " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithExchange(McpAsyncServerExchange exchange,
				CompleteRequest request) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion with exchange for " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithArgument(CompleteRequest.CompleteArgument argument) {
			return Mono.just(new CompleteResult(
					new CompleteCompletion(List.of("Async completion from argument: " + argument.value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithValue(String value) {
			return Mono.just(new CompleteResult(
					new CompleteCompletion(List.of("Async completion from value: " + value), 1, false)));
		}

		@McpComplete(prompt = "test-prompt")
		public Mono<CompleteResult> getCompletionWithPrompt(CompleteRequest request) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion for prompt with: " + request.argument().value()), 1, false)));
		}

		@McpComplete(uri = "test://{variable}")
		public Mono<CompleteResult> getCompletionWithUri(CompleteRequest request) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion for URI with: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteCompletion> getCompletionObject(CompleteRequest request) {
			return Mono.just(new CompleteCompletion(
					List.of("Async completion object for: " + request.argument().value()), 1, false));
		}

		public Mono<List<String>> getCompletionList(CompleteRequest request) {
			return Mono.just(List.of("Async list item 1 for: " + request.argument().value(),
					"Async list item 2 for: " + request.argument().value()));
		}

		public Mono<String> getCompletionString(CompleteRequest request) {
			return Mono.just("Async string completion for: " + request.argument().value());
		}

		// Non-reactive methods
		public CompleteResult getDirectCompletionResult(CompleteRequest request) {
			return new CompleteResult(
					new CompleteCompletion(List.of("Direct completion for " + request.argument().value()), 1, false));
		}

		public CompleteCompletion getDirectCompletionObject(CompleteRequest request) {
			return new CompleteCompletion(List.of("Direct completion object for: " + request.argument().value()), 1,
					false);
		}

		public List<String> getDirectCompletionList(CompleteRequest request) {
			return List.of("Direct list item 1 for: " + request.argument().value(),
					"Direct list item 2 for: " + request.argument().value());
		}

		public String getDirectCompletionString(CompleteRequest request) {
			return "Direct string completion for: " + request.argument().value();
		}

		public void invalidReturnType(CompleteRequest request) {
			// Invalid return type
		}

		public Mono<CompleteResult> invalidParameters(int value) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> tooManyParameters(McpAsyncServerExchange exchange, CompleteRequest request,
				String extraParam, String extraParam2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> invalidParameterType(Object invalidParam) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> duplicateExchangeParameters(McpAsyncServerExchange exchange1,
				McpAsyncServerExchange exchange2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> duplicateRequestParameters(CompleteRequest request1, CompleteRequest request2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> duplicateArgumentParameters(CompleteRequest.CompleteArgument arg1,
				CompleteRequest.CompleteArgument arg2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> getCompletionWithProgressToken(@McpProgressToken String progressToken,
				CompleteRequest request) {
			String tokenInfo = progressToken != null ? " (token: " + progressToken + ")" : " (no token)";
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion with progress" + tokenInfo + " for: " + request.argument().value()), 1,
					false)));
		}

		public Mono<CompleteResult> getCompletionWithMixedAndProgress(McpAsyncServerExchange exchange,
				@McpProgressToken String progressToken, String value, CompleteRequest request) {
			String tokenInfo = progressToken != null ? " (token: " + progressToken + ")" : " (no token)";
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of("Async mixed completion" + tokenInfo
					+ " with value: " + value + " and request: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> duplicateProgressTokenParameters(@McpProgressToken String token1,
				@McpProgressToken String token2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> getCompletionWithMeta(McpMeta meta, CompleteRequest request) {
			String metaInfo = meta != null && meta.get("key") != null ? " (meta: " + meta.get("key") + ")"
					: " (no meta)";
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion with meta" + metaInfo + " for: " + request.argument().value()), 1,
					false)));
		}

		public Mono<CompleteResult> getCompletionWithMetaAndMixed(McpAsyncServerExchange exchange, McpMeta meta,
				String value, CompleteRequest request) {
			String metaInfo = meta != null && meta.get("key") != null ? " (meta: " + meta.get("key") + ")"
					: " (no meta)";
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of("Async mixed completion" + metaInfo
					+ " with value: " + value + " and request: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> duplicateMetaParameters(McpMeta meta1, McpMeta meta2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> getCompletionWithAsyncRequestContext(McpAsyncRequestContext context) {
			CompleteRequest request = (CompleteRequest) context.request();
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion with async context for: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithAsyncRequestContextAndValue(McpAsyncRequestContext context,
				String value) {
			CompleteRequest request = (CompleteRequest) context.request();
			return Mono.just(new CompleteResult(new CompleteCompletion(List
				.of("Async completion with async context and value: " + value + " for: " + request.argument().value()),
					1, false)));
		}

		public Mono<CompleteResult> duplicateAsyncRequestContextParameters(McpAsyncRequestContext context1,
				McpAsyncRequestContext context2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> invalidSyncRequestContextInAsyncMethod(McpSyncRequestContext context) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public CompleteResult invalidAsyncRequestContextInSyncMethod(McpAsyncRequestContext context) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public Mono<CompleteResult> getCompletionWithTransportContext(McpTransportContext transportContext,
				CompleteRequest request) {
			if (transportContext == null) {
				return Mono.error(new IllegalStateException("Transport context must not be null"));
			}
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async completion with transport context for " + request.argument().value()), 1, false)));
		}

	}

}
