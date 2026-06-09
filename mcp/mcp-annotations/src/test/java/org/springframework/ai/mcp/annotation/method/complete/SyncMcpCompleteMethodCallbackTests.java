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
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

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
 * Tests for {@link SyncMcpCompleteMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpCompleteMethodCallbackTests {

	@Test
	public void testCallbackWithRequestParameter() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion for value");
	}

	@Test
	public void testCallbackWithExchangeAndRequestParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithExchange", McpSyncServerExchange.class,
				CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion with exchange for value");
	}

	@Test
	public void testCallbackWithArgumentParameter() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithArgument",
				CompleteRequest.CompleteArgument.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion from argument: value");
	}

	@Test
	public void testCallbackWithValueParameter() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithValue", String.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion from value: value");
	}

	@Test
	public void testCallbackWithPromptAnnotation() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithPrompt", CompleteRequest.class);
		McpComplete completeAnnotation = method.getAnnotation(McpComplete.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.complete(completeAnnotation)
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion for prompt with: value");
	}

	@Test
	public void testCallbackWithUriAnnotation() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithUri", CompleteRequest.class);
		McpComplete completeAnnotation = method.getAnnotation(McpComplete.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.complete(completeAnnotation)
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new ResourceReference("test://value"),
				new CompleteRequest.CompleteArgument("variable", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion for URI with: value");
	}

	@Test
	public void testCallbackWithCompletionObject() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionObject", CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion object for: value");
	}

	@Test
	public void testCallbackWithCompletionList() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionList", CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(2);
		assertThat(result.completion().values().get(0)).isEqualTo("List item 1 for: value");
		assertThat(result.completion().values().get(1)).isEqualTo("List item 2 for: value");
	}

	@Test
	public void testCallbackWithCompletionString() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionString", CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("String completion for: value");
	}

	@Test
	public void testInvalidReturnType() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("invalidReturnType", CompleteRequest.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Method must return either CompleteResult, CompleteCompletion, List<String>, or String");
	}

	@Test
	public void testInvalidParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("invalidParameters", int.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method parameters must be exchange, CompleteRequest, CompleteArgument, or String");
	}

	@Test
	public void testTooManyParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("tooManyParameters", McpSyncServerExchange.class,
				CompleteRequest.class, String.class, String.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method can have at most 3 input parameters");
	}

	@Test
	public void testInvalidParameterType() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("invalidParameterType", Object.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method parameters must be exchange, CompleteRequest, CompleteArgument, or String");
	}

	@Test
	public void testDuplicateExchangeParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("duplicateExchangeParameters", McpSyncServerExchange.class,
				McpSyncServerExchange.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one exchange parameter");
	}

	@Test
	public void testDuplicateRequestParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("duplicateRequestParameters", CompleteRequest.class,
				CompleteRequest.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one CompleteRequest parameter");
	}

	@Test
	public void testDuplicateArgumentParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("duplicateArgumentParameters",
				CompleteRequest.CompleteArgument.class, CompleteRequest.CompleteArgument.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one CompleteArgument parameter");
	}

	@Test
	public void testMissingPromptAndUri() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Either prompt or uri must be provided");
	}

	@Test
	public void testBothPromptAndUri() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.uri("test://resource")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Only one of prompt or uri can be provided");
	}

	@Test
	public void testNullRequest() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithRequest", CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		assertThatThrownBy(() -> callback.apply(exchange, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Request must not be null");
	}

	@Test
	public void testCallbackWithProgressToken() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithProgressToken", String.class,
				CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		// Since CompleteRequest doesn't have progressToken, it should be null
		assertThat(result.completion().values().get(0)).isEqualTo("Completion with progress (no token) for: value");
	}

	@Test
	public void testCallbackWithMixedAndProgressToken() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithMixedAndProgress",
				McpSyncServerExchange.class, String.class, String.class, CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		// Since CompleteRequest doesn't have progressToken, it should be null
		assertThat(result.completion().values().get(0))
			.isEqualTo("Mixed completion (no token) with value: value and request: value");
	}

	@Test
	public void testDuplicateProgressTokenParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("duplicateProgressTokenParameters", String.class,
				String.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one @McpProgressToken parameter");
	}

	@Test
	public void testCallbackWithMeta() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithMeta", McpMeta.class,
				CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"), java.util.Map.of("key", "test-value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion with meta (meta: test-value) for: value");
	}

	@Test
	public void testCallbackWithMetaNull() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithMeta", McpMeta.class,
				CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion with meta (no meta) for: value");
	}

	@Test
	public void testCallbackWithMetaAndMixed() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithMetaAndMixed",
				McpSyncServerExchange.class, McpMeta.class, String.class, CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"), java.util.Map.of("key", "test-value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0))
			.isEqualTo("Mixed completion (meta: test-value) with value: value and request: value");
	}

	@Test
	public void testDuplicateMetaParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("duplicateMetaParameters", McpMeta.class, McpMeta.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one McpMeta parameter");
	}

	@Test
	public void testCallbackWithSyncRequestContext() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithSyncRequestContext",
				McpSyncRequestContext.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion with sync context for: value");
	}

	@Test
	public void testCallbackWithSyncRequestContextAndValue() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithSyncRequestContextAndValue",
				McpSyncRequestContext.class, String.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0))
			.isEqualTo("Completion with sync context and value: value for: value");
	}

	@Test
	public void testDuplicateSyncRequestContextParameters() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("duplicateSyncRequestContextParameters",
				McpSyncRequestContext.class, McpSyncRequestContext.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one request context parameter");
	}

	@Test
	public void testInvalidAsyncRequestContextInSyncMethod() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("invalidAsyncRequestContextInSyncMethod",
				McpAsyncRequestContext.class);

		assertThatThrownBy(() -> SyncMcpCompleteMethodCallback.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Sync complete methods should use McpSyncRequestContext instead of McpAsyncRequestContext parameter");
	}

	@Test
	public void testCallbackWithProgressTokenNonNull() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithProgressToken", String.class,
				CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		// Create a CompleteRequest with progressToken using reflection or a builder
		// pattern
		// Since the exact constructor signature is not clear, we'll test with a mock that
		// returns the progressToken
		CompleteRequest request = mock(CompleteRequest.class);
		when(request.ref()).thenReturn(new PromptReference("test-prompt"));
		when(request.argument()).thenReturn(new CompleteRequest.CompleteArgument("test", "value"));
		when(request.progressToken()).thenReturn("progress-123");

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0))
			.isEqualTo("Completion with progress (token: progress-123) for: value");
	}

	@Test
	public void testCallbackWithTransportContextParameter() throws Exception {
		TestCompleteProvider provider = new TestCompleteProvider();
		Method method = TestCompleteProvider.class.getMethod("getCompletionWithTransportContext",
				McpTransportContext.class, CompleteRequest.class);

		BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> callback = SyncMcpCompleteMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.prompt("test-prompt")
			.build();

		McpTransportContext transportContext = mock(McpTransportContext.class);
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		when(exchange.transportContext()).thenReturn(transportContext);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));

		CompleteResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.completion()).isNotNull();
		assertThat(result.completion().values()).hasSize(1);
		assertThat(result.completion().values().get(0)).isEqualTo("Completion with transport context for value");
	}

	private static class TestCompleteProvider {

		public CompleteResult getCompletionWithRequest(CompleteRequest request) {
			return new CompleteResult(
					new CompleteCompletion(List.of("Completion for " + request.argument().value()), 1, false));
		}

		public CompleteResult getCompletionWithExchange(McpSyncServerExchange exchange, CompleteRequest request) {
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion with exchange for " + request.argument().value()), 1, false));
		}

		public CompleteResult getCompletionWithArgument(CompleteRequest.CompleteArgument argument) {
			return new CompleteResult(
					new CompleteCompletion(List.of("Completion from argument: " + argument.value()), 1, false));
		}

		public CompleteResult getCompletionWithValue(String value) {
			return new CompleteResult(new CompleteCompletion(List.of("Completion from value: " + value), 1, false));
		}

		@McpComplete(prompt = "test-prompt")
		public CompleteResult getCompletionWithPrompt(CompleteRequest request) {
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion for prompt with: " + request.argument().value()), 1, false));
		}

		@McpComplete(uri = "test://{variable}")
		public CompleteResult getCompletionWithUri(CompleteRequest request) {
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion for URI with: " + request.argument().value()), 1, false));
		}

		public CompleteCompletion getCompletionObject(CompleteRequest request) {
			return new CompleteCompletion(List.of("Completion object for: " + request.argument().value()), 1, false);
		}

		public List<String> getCompletionList(CompleteRequest request) {
			return List.of("List item 1 for: " + request.argument().value(),
					"List item 2 for: " + request.argument().value());
		}

		public String getCompletionString(CompleteRequest request) {
			return "String completion for: " + request.argument().value();
		}

		public void invalidReturnType(CompleteRequest request) {
			// Invalid return type
		}

		public CompleteResult invalidParameters(int value) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult tooManyParameters(McpSyncServerExchange exchange, CompleteRequest request,
				String extraParam, String extraParam2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult invalidParameterType(Object invalidParam) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult duplicateExchangeParameters(McpSyncServerExchange exchange1,
				McpSyncServerExchange exchange2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult duplicateRequestParameters(CompleteRequest request1, CompleteRequest request2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult duplicateArgumentParameters(CompleteRequest.CompleteArgument arg1,
				CompleteRequest.CompleteArgument arg2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult getCompletionWithProgressToken(@McpProgressToken String progressToken,
				CompleteRequest request) {
			String tokenInfo = progressToken != null ? " (token: " + progressToken + ")" : " (no token)";
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion with progress" + tokenInfo + " for: " + request.argument().value()), 1, false));
		}

		public CompleteResult getCompletionWithMixedAndProgress(McpSyncServerExchange exchange,
				@McpProgressToken String progressToken, String value, CompleteRequest request) {
			String tokenInfo = progressToken != null ? " (token: " + progressToken + ")" : " (no token)";
			return new CompleteResult(new CompleteCompletion(List.of("Mixed completion" + tokenInfo + " with value: "
					+ value + " and request: " + request.argument().value()), 1, false));
		}

		public CompleteResult duplicateProgressTokenParameters(@McpProgressToken String token1,
				@McpProgressToken String token2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult getCompletionWithMeta(McpMeta meta, CompleteRequest request) {
			String metaInfo = meta != null && meta.get("key") != null ? " (meta: " + meta.get("key") + ")"
					: " (no meta)";
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion with meta" + metaInfo + " for: " + request.argument().value()), 1, false));
		}

		public CompleteResult getCompletionWithMetaAndMixed(McpSyncServerExchange exchange, McpMeta meta, String value,
				CompleteRequest request) {
			String metaInfo = meta != null && meta.get("key") != null ? " (meta: " + meta.get("key") + ")"
					: " (no meta)";
			return new CompleteResult(new CompleteCompletion(List.of("Mixed completion" + metaInfo + " with value: "
					+ value + " and request: " + request.argument().value()), 1, false));
		}

		public CompleteResult duplicateMetaParameters(McpMeta meta1, McpMeta meta2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult getCompletionWithSyncRequestContext(McpSyncRequestContext context) {
			CompleteRequest request = (CompleteRequest) context.request();
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion with sync context for: " + request.argument().value()), 1, false));
		}

		public CompleteResult getCompletionWithSyncRequestContextAndValue(McpSyncRequestContext context, String value) {
			CompleteRequest request = (CompleteRequest) context.request();
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion with sync context and value: " + value + " for: " + request.argument().value()),
					1, false));
		}

		public CompleteResult duplicateSyncRequestContextParameters(McpSyncRequestContext context1,
				McpSyncRequestContext context2) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public CompleteResult invalidAsyncRequestContextInSyncMethod(McpAsyncRequestContext context) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		public Mono<CompleteResult> invalidSyncRequestContextInAsyncMethod(McpSyncRequestContext context) {
			return Mono.just(new CompleteResult(new CompleteCompletion(List.of(), 0, false)));
		}

		public CompleteResult getCompletionWithTransportContext(McpTransportContext transportContext,
				CompleteRequest request) {
			if (transportContext == null) {
				throw new IllegalStateException("Transport context must not be null");
			}
			return new CompleteResult(new CompleteCompletion(
					List.of("Completion with transport context for " + request.argument().value()), 1, false));
		}

	}

}
