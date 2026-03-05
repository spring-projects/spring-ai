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

package org.springframework.ai.mcp.annotation.provider.complete;

import java.util.List;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpComplete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AsyncMcpCompleteProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpCompletionProviderTests {

	@Test
	void testConstructorWithNullCompleteObjects() {
		assertThatThrownBy(() -> new AsyncMcpCompleteProvider(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("completeObjects cannot be null");
	}

	@Test
	void testGetCompleteSpecificationsWithSingleValidComplete() {
		// Create a class with only one valid async complete method
		class SingleValidComplete {

			@McpComplete(prompt = "test-prompt")
			public Mono<CompleteResult> testComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Async completion for " + request.argument().value()), 1, false)));
			}

		}

		SingleValidComplete completeObject = new SingleValidComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).isNotNull();
		assertThat(completeSpecs).hasSize(1);

		AsyncCompletionSpecification completeSpec = completeSpecs.get(0);
		assertThat(completeSpec.referenceKey()).isInstanceOf(PromptReference.class);
		PromptReference promptRef = (PromptReference) completeSpec.referenceKey();
		assertThat(promptRef.name()).isEqualTo("test-prompt");
		assertThat(completeSpec.completionHandler()).isNotNull();

		// Test that the handler works
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("test-prompt"),
				new CompleteRequest.CompleteArgument("test", "value"));
		Mono<CompleteResult> result = completeSpec.completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(1);
			assertThat(completeResult.completion().values().get(0)).isEqualTo("Async completion for value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsWithUriReference() {
		class UriComplete {

			@McpComplete(uri = "test://{variable}")
			public Mono<CompleteResult> uriComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Async URI completion for " + request.argument().value()), 1, false)));
			}

		}

		UriComplete completeObject = new UriComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		assertThat(completeSpecs.get(0).referenceKey()).isInstanceOf(ResourceReference.class);
		ResourceReference resourceRef = (ResourceReference) completeSpecs.get(0).referenceKey();
		assertThat(resourceRef.uri()).isEqualTo("test://{variable}");

		// Test that the handler works
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new ResourceReference("test://value"),
				new CompleteRequest.CompleteArgument("variable", "value"));
		Mono<CompleteResult> result = completeSpecs.get(0).completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(1);
			assertThat(completeResult.completion().values().get(0)).isEqualTo("Async URI completion for value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsFiltersOutNonReactiveReturnTypes() {
		class MixedReturnComplete {

			@McpComplete(prompt = "sync-complete")
			public CompleteResult syncComplete(CompleteRequest request) {
				return new CompleteResult(
						new CompleteCompletion(List.of("Sync completion for " + request.argument().value()), 1, false));
			}

			@McpComplete(prompt = "async-complete")
			public Mono<CompleteResult> asyncComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Async completion for " + request.argument().value()), 1, false)));
			}

		}

		MixedReturnComplete completeObject = new MixedReturnComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("async-complete");
	}

	@Test
	void testGetCompleteSpecificationsWithMultipleCompleteMethods() {
		class MultipleCompleteMethods {

			@McpComplete(prompt = "complete1")
			public Mono<CompleteResult> firstComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("First completion for " + request.argument().value()), 1, false)));
			}

			@McpComplete(prompt = "complete2")
			public Mono<CompleteResult> secondComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Second completion for " + request.argument().value()), 1, false)));
			}

		}

		MultipleCompleteMethods completeObject = new MultipleCompleteMethods();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(2);
		PromptReference promptRef1 = (PromptReference) completeSpecs.get(0).referenceKey();
		PromptReference promptRef2 = (PromptReference) completeSpecs.get(1).referenceKey();
		assertThat(promptRef1.name()).isIn("complete1", "complete2");
		assertThat(promptRef2.name()).isIn("complete1", "complete2");
		assertThat(promptRef1.name()).isNotEqualTo(promptRef2.name());
	}

	@Test
	void testGetCompleteSpecificationsWithMultipleCompleteObjects() {
		class FirstCompleteObject {

			@McpComplete(prompt = "first-complete")
			public Mono<CompleteResult> firstComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("First completion for " + request.argument().value()), 1, false)));
			}

		}

		class SecondCompleteObject {

			@McpComplete(prompt = "second-complete")
			public Mono<CompleteResult> secondComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Second completion for " + request.argument().value()), 1, false)));
			}

		}

		FirstCompleteObject firstObject = new FirstCompleteObject();
		SecondCompleteObject secondObject = new SecondCompleteObject();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(firstObject, secondObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(2);
		PromptReference promptRef1 = (PromptReference) completeSpecs.get(0).referenceKey();
		PromptReference promptRef2 = (PromptReference) completeSpecs.get(1).referenceKey();
		assertThat(promptRef1.name()).isIn("first-complete", "second-complete");
		assertThat(promptRef2.name()).isIn("first-complete", "second-complete");
		assertThat(promptRef1.name()).isNotEqualTo(promptRef2.name());
	}

	@Test
	void testGetCompleteSpecificationsWithMixedMethods() {
		class MixedMethods {

			@McpComplete(prompt = "valid-complete")
			public Mono<CompleteResult> validComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Valid completion for " + request.argument().value()), 1, false)));
			}

			public CompleteResult nonAnnotatedMethod(CompleteRequest request) {
				return new CompleteResult(new CompleteCompletion(
						List.of("Non-annotated completion for " + request.argument().value()), 1, false));
			}

			@McpComplete(prompt = "sync-complete")
			public CompleteResult syncComplete(CompleteRequest request) {
				return new CompleteResult(
						new CompleteCompletion(List.of("Sync completion for " + request.argument().value()), 1, false));
			}

		}

		MixedMethods completeObject = new MixedMethods();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("valid-complete");
	}

	@Test
	void testGetCompleteSpecificationsWithPrivateMethod() {
		class PrivateMethodComplete {

			@McpComplete(prompt = "private-complete")
			private Mono<CompleteResult> privateComplete(CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(
						List.of("Private completion for " + request.argument().value()), 1, false)));
			}

		}

		PrivateMethodComplete completeObject = new PrivateMethodComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("private-complete");

		// Test that the handler works with private methods
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("private-complete"),
				new CompleteRequest.CompleteArgument("test", "value"));
		Mono<CompleteResult> result = completeSpecs.get(0).completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(1);
			assertThat(completeResult.completion().values().get(0)).isEqualTo("Private completion for value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsWithMonoStringReturn() {
		class MonoStringReturnComplete {

			@McpComplete(prompt = "mono-string-complete")
			public Mono<String> monoStringComplete(CompleteRequest request) {
				return Mono.just("Simple string completion for " + request.argument().value());
			}

		}

		MonoStringReturnComplete completeObject = new MonoStringReturnComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("mono-string-complete");

		// Test that the handler works with Mono<String> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("mono-string-complete"),
				new CompleteRequest.CompleteArgument("test", "value"));
		Mono<CompleteResult> result = completeSpecs.get(0).completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(1);
			assertThat(completeResult.completion().values().get(0)).isEqualTo("Simple string completion for value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsWithExchangeParameter() {
		class ExchangeParameterComplete {

			@McpComplete(prompt = "exchange-complete")
			public Mono<CompleteResult> exchangeComplete(McpAsyncServerExchange exchange, CompleteRequest request) {
				return Mono.just(new CompleteResult(new CompleteCompletion(List.of("Completion with exchange: "
						+ (exchange != null ? "present" : "null") + ", value: " + request.argument().value()), 1,
						false)));
			}

		}

		ExchangeParameterComplete completeObject = new ExchangeParameterComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("exchange-complete");

		// Test that the handler works with exchange parameter
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("exchange-complete"),
				new CompleteRequest.CompleteArgument("test", "value"));
		Mono<CompleteResult> result = completeSpecs.get(0).completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(1);
			assertThat(completeResult.completion().values().get(0))
				.isEqualTo("Completion with exchange: present, value: value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsWithMonoListReturn() {
		class MonoListReturnComplete {

			@McpComplete(prompt = "mono-list-complete")
			public Mono<List<String>> monoListComplete(CompleteRequest request) {
				return Mono.just(List.of("First completion for " + request.argument().value(),
						"Second completion for " + request.argument().value()));
			}

		}

		MonoListReturnComplete completeObject = new MonoListReturnComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("mono-list-complete");

		// Test that the handler works with Mono<List<String>> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("mono-list-complete"),
				new CompleteRequest.CompleteArgument("test", "value"));
		Mono<CompleteResult> result = completeSpecs.get(0).completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(2);
			assertThat(completeResult.completion().values().get(0)).isEqualTo("First completion for value");
			assertThat(completeResult.completion().values().get(1)).isEqualTo("Second completion for value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsWithMonoCompletionReturn() {
		class MonoCompletionReturnComplete {

			@McpComplete(prompt = "mono-completion-complete")
			public Mono<CompleteCompletion> monoCompletionComplete(CompleteRequest request) {
				return Mono.just(new CompleteCompletion(List.of("Completion object for " + request.argument().value()),
						1, false));
			}

		}

		MonoCompletionReturnComplete completeObject = new MonoCompletionReturnComplete();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).hasSize(1);
		PromptReference promptRef = (PromptReference) completeSpecs.get(0).referenceKey();
		assertThat(promptRef.name()).isEqualTo("mono-completion-complete");

		// Test that the handler works with Mono<CompleteCompletion> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CompleteRequest request = new CompleteRequest(new PromptReference("mono-completion-complete"),
				new CompleteRequest.CompleteArgument("test", "value"));
		Mono<CompleteResult> result = completeSpecs.get(0).completionHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(completeResult -> {
			assertThat(completeResult).isNotNull();
			assertThat(completeResult.completion()).isNotNull();
			assertThat(completeResult.completion().values()).hasSize(1);
			assertThat(completeResult.completion().values().get(0)).isEqualTo("Completion object for value");
		}).verifyComplete();
	}

	@Test
	void testGetCompleteSpecificationsWithEmptyList() {
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of());

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).isNotNull();
		assertThat(completeSpecs).isEmpty();
	}

	@Test
	void testGetCompleteSpecificationsWithNoValidMethods() {
		class NoValidMethods {

			public void voidMethod() {
				// No return value
			}

			public String nonAnnotatedMethod() {
				return "Not annotated";
			}

		}

		NoValidMethods completeObject = new NoValidMethods();
		AsyncMcpCompleteProvider provider = new AsyncMcpCompleteProvider(List.of(completeObject));

		List<AsyncCompletionSpecification> completeSpecs = provider.getCompleteSpecifications();

		assertThat(completeSpecs).isNotNull();
		assertThat(completeSpecs).isEmpty();
	}

}
