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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AsyncStatelessMcpCompleteMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class AsyncStatelessMcpCompleteMethodCallbackTests {

	@Test
	public void testCallbackWithRequestParameter() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithRequest",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async stateless completion for value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithContextAndRequestParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithContext",
				McpTransportContext.class, CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless completion with context for value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithArgumentParameter() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithArgument",
				CompleteRequest.CompleteArgument.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless completion from argument: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithValueParameter() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithValue", String.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async stateless completion from value: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithPromptAnnotation() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithPrompt",
				CompleteRequest.class);
		McpComplete completeAnnotation = method.getAnnotation(McpComplete.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.complete(completeAnnotation)
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless completion for prompt with: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithUriAnnotation() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithUri",
				CompleteRequest.class);
		McpComplete completeAnnotation = method.getAnnotation(McpComplete.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.complete(completeAnnotation)
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new ResourceReference("test://value"),
				new CompleteRequest.CompleteArgument("variable", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async stateless completion for URI with: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithCompletionObject() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionObject",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async stateless completion object for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithCompletionList() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionList", CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(2);
			assertThat(result.completion().values().get(0)).isEqualTo("Async stateless list item 1 for: value");
			assertThat(result.completion().values().get(1)).isEqualTo("Async stateless list item 2 for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithCompletionString() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionString",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Async stateless string completion for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionResult() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getDirectCompletionResult",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct stateless completion for value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionObject() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getDirectCompletionObject",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct stateless completion object for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionList() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getDirectCompletionList",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(2);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct stateless list item 1 for: value");
			assertThat(result.completion().values().get(1)).isEqualTo("Direct stateless list item 2 for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithDirectCompletionString() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getDirectCompletionString",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0)).isEqualTo("Direct stateless string completion for: value");
		}).verifyComplete();
	}

	@Test
	public void testInvalidReturnType() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("invalidReturnType", CompleteRequest.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Method must return either CompleteResult, CompleteCompletion, List<String>, String, or Mono<T>");
	}

	@Test
	public void testInvalidParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("invalidParameters", int.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method parameters must be exchange, CompleteRequest, CompleteArgument, or String");
	}

	@Test
	public void testTooManyParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("tooManyParameters",
				McpTransportContext.class, CompleteRequest.class, String.class, String.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method can have at most 3 input parameters");
	}

	@Test
	public void testInvalidParameterType() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("invalidParameterType", Object.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method parameters must be exchange, CompleteRequest, CompleteArgument, or String");
	}

	@Test
	public void testDuplicateContextParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("duplicateContextParameters",
				McpTransportContext.class, McpTransportContext.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one transport context parameter");
	}

	@Test
	public void testDuplicateRequestParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("duplicateRequestParameters",
				CompleteRequest.class, CompleteRequest.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one CompleteRequest parameter");
	}

	@Test
	public void testDuplicateArgumentParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("duplicateArgumentParameters",
				CompleteRequest.CompleteArgument.class, CompleteRequest.CompleteArgument.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one CompleteArgument parameter");
	}

	@Test
	public void testMissingPromptAndUri() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithRequest",
				CompleteRequest.class);

		assertThatThrownBy(
				() -> AsyncStatelessMcpCompleteMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Either prompt or uri must be provided");
	}

	@Test
	public void testBothPromptAndUri() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithRequest",
				CompleteRequest.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.uri("test://resource")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Only one of prompt or uri can be provided");
	}

	@Test
	public void testNullRequest() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithRequest",
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);

		StepVerifier.create(callback.apply(context, null))
			.expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
					&& throwable.getMessage().contains("Request must not be null"))
			.verify();
	}

	@Test
	public void testCallbackWithProgressToken() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithProgressToken",
				String.class, CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			// Since CompleteRequest doesn't have progressToken, it should be null
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless completion with progress (no token) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMixedAndProgressToken() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithMixedAndProgress",
				McpTransportContext.class, String.class, String.class, CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			// Since CompleteRequest doesn't have progressToken, it should be null
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless mixed completion (no token) with value: value and request: value");
		}).verifyComplete();
	}

	@Test
	public void testDuplicateProgressTokenParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("duplicateProgressTokenParameters",
				String.class, String.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one @McpProgressToken parameter");
	}

	@Test
	public void testCallbackWithMeta() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithMeta", McpMeta.class,
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"), java.util.Map.of("key", "test-value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless completion with meta (meta: test-value) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaNull() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithMeta", McpMeta.class,
				CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless completion with meta (no meta) for: value");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaAndMixed() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("getCompletionWithMetaAndMixed",
				McpTransportContext.class, McpMeta.class, String.class, CompleteRequest.class);

		BiFunction<McpTransportContext, CompleteRequest, Mono<CompleteResult>> callback = AsyncStatelessMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"), java.util.Map.of("key", "test-value"));

		Mono<CompleteResult> resultMono = callback.apply(context, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.completion()).isNotNull();
			assertThat(result.completion().values()).hasSize(1);
			assertThat(result.completion().values().get(0))
				.isEqualTo("Async stateless mixed completion (meta: test-value) with value: value and request: value");
		}).verifyComplete();
	}

	@Test
	public void testDuplicateMetaParameters() throws Exception {
		TestAsyncStatelessCompleteProvider provider = new TestAsyncStatelessCompleteProvider();
		Method method = TestAsyncStatelessCompleteProvider.class.getMethod("duplicateMetaParameters", McpMeta.class,
				McpMeta.class);

		assertThatThrownBy(() -> AsyncStatelessMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one McpMeta parameter");
	}

	private static class TestAsyncStatelessCompleteProvider {

		public Mono<CompleteResult> getCompletionWithRequest(CompleteRequest request) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async stateless completion for " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithContext(McpTransportContext context, CompleteRequest request) {
			if (context == null) {
				return Mono.error(new IllegalStateException("Transport context must not be null"));
			}
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async stateless completion with context for " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithArgument(CompleteRequest.CompleteArgument argument) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async stateless completion from argument: " + argument.value()), 1, false)));
		}

		public Mono<CompleteResult> getCompletionWithValue(String value) {
			return Mono.just(new CompleteResult(
					new CompleteCompletion(List.of("Async stateless completion from value: " + value), 1, false)));
		}

		@McpComplete(prompt = "test-prompt")
		public Mono<CompleteResult> getCompletionWithPrompt(CompleteRequest request) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async stateless completion for prompt with: " + request.argument().value()), 1, false)));
		}

		@McpComplete(uri = "test://{variable}")
		public Mono<CompleteResult> getCompletionWithUri(CompleteRequest request) {
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async stateless completion for URI with: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteCompletion> getCompletionObject(CompleteRequest request) {
			return Mono.just(new CompleteCompletion(
					List.of("Async stateless completion object for: " + request.argument().value()), 1, false));
		}

		public Mono<List<String>> getCompletionList(CompleteRequest request) {
			return Mono.just(List.of("Async stateless list item 1 for: " + request.argument().value(),
					"Async stateless list item 2 for: " + request.argument().value()));
		}

		public Mono<String> getCompletionString(CompleteRequest request) {
			return Mono.just("Async stateless string completion for: " + request.argument().value());
		}

		// Non-reactive methods
		public CompleteResult getDirectCompletionResult(CompleteRequest request) {
			return new CompleteResult(new CompleteCompletion(
					List.of("Direct stateless completion for " + request.argument().value()), 1, false));
		}

		public CompleteCompletion getDirectCompletionObject(CompleteRequest request) {
			return new CompleteCompletion(
					List.of("Direct stateless completion object for: " + request.argument().value()), 1, false);
		}

		public List<String> getDirectCompletionList(CompleteRequest request) {
			return List.of("Direct stateless list item 1 for: " + request.argument().value(),
					"Direct stateless list item 2 for: " + request.argument().value());
		}

		public String getDirectCompletionString(CompleteRequest request) {
			return "Direct stateless string completion for: " + request.argument().value();
		}

		public void invalidReturnType(CompleteRequest request) {
			// Invalid return type
		}

		public Mono<CompleteResult> invalidParameters(int value) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> tooManyParameters(McpTransportContext context, CompleteRequest request,
				String extraParam, String extraParam2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> invalidParameterType(Object invalidParam) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> duplicateContextParameters(McpTransportContext context1,
				McpTransportContext context2) {
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
			return Mono.just(new CompleteResult(new CompleteCompletion(List
				.of("Async stateless completion with progress" + tokenInfo + " for: " + request.argument().value()), 1,
					false)));
		}

		public Mono<CompleteResult> getCompletionWithMixedAndProgress(McpTransportContext context,
				@McpProgressToken String progressToken, String value, CompleteRequest request) {
			String tokenInfo = progressToken != null ? " (token: " + progressToken + ")" : " (no token)";
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of("Async stateless mixed completion"
					+ tokenInfo + " with value: " + value + " and request: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> duplicateProgressTokenParameters(@McpProgressToken String token1,
				@McpProgressToken String token2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public Mono<CompleteResult> getCompletionWithMeta(McpMeta meta, CompleteRequest request) {
			String metaInfo = meta != null && meta.get("key") != null ? " (meta: " + meta.get("key") + ")"
					: " (no meta)";
			return Mono.just(new CompleteResult(new CompleteCompletion(
					List.of("Async stateless completion with meta" + metaInfo + " for: " + request.argument().value()),
					1, false)));
		}

		public Mono<CompleteResult> getCompletionWithMetaAndMixed(McpTransportContext context, McpMeta meta,
				String value, CompleteRequest request) {
			String metaInfo = meta != null && meta.get("key") != null ? " (meta: " + meta.get("key") + ")"
					: " (no meta)";
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of("Async stateless mixed completion"
					+ metaInfo + " with value: " + value + " and request: " + request.argument().value()), 1, false)));
		}

		public Mono<CompleteResult> duplicateMetaParameters(McpMeta meta1, McpMeta meta2) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

	}

}
