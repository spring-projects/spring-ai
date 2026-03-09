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

package org.springframework.ai.mcp.annotation.provider.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AsyncMcpPromptProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpPromptProviderTests {

	@Test
	void testConstructorWithNullPromptObjects() {
		assertThatThrownBy(() -> new AsyncMcpPromptProvider(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("promptObjects cannot be null");
	}

	@Test
	void testGetPromptSpecificationsWithSingleValidPrompt() {
		// Create a class with only one valid async prompt method
		class SingleValidPrompt {

			@McpPrompt(name = "test-prompt", description = "A test prompt")
			public Mono<GetPromptResult> testPrompt(GetPromptRequest request) {
				return Mono.just(new GetPromptResult("Test prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Hello from " + request.name())))));
			}

		}

		SingleValidPrompt promptObject = new SingleValidPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).isNotNull();
		assertThat(promptSpecs).hasSize(1);

		AsyncPromptSpecification promptSpec = promptSpecs.get(0);
		assertThat(promptSpec.prompt().name()).isEqualTo("test-prompt");
		assertThat(promptSpec.prompt().description()).isEqualTo("A test prompt");
		assertThat(promptSpec.promptHandler()).isNotNull();

		// Test that the handler works
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		args.put("name", "John");
		GetPromptRequest request = new GetPromptRequest("test-prompt", args);
		Mono<GetPromptResult> result = promptSpec.promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.description()).isEqualTo("Test prompt result");
			assertThat(promptResult.messages()).hasSize(1);
			PromptMessage message = promptResult.messages().get(0);
			assertThat(message.role()).isEqualTo(Role.ASSISTANT);
			assertThat(((TextContent) message.content()).text()).isEqualTo("Hello from test-prompt");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithCustomPromptName() {
		class CustomNamePrompt {

			@McpPrompt(name = "custom-name", description = "Custom named prompt")
			public Mono<GetPromptResult> methodWithDifferentName() {
				return Mono.just(new GetPromptResult("Custom prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Custom prompt content")))));
			}

		}

		CustomNamePrompt promptObject = new CustomNamePrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("custom-name");
		assertThat(promptSpecs.get(0).prompt().description()).isEqualTo("Custom named prompt");
	}

	@Test
	void testGetPromptSpecificationsWithDefaultPromptName() {
		class DefaultNamePrompt {

			@McpPrompt(description = "Prompt with default name")
			public Mono<GetPromptResult> defaultNameMethod() {
				return Mono.just(new GetPromptResult("Default prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Default prompt content")))));
			}

		}

		DefaultNamePrompt promptObject = new DefaultNamePrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("defaultNameMethod");
		assertThat(promptSpecs.get(0).prompt().description()).isEqualTo("Prompt with default name");
	}

	@Test
	void testGetPromptSpecificationsWithEmptyPromptName() {
		class EmptyNamePrompt {

			@McpPrompt(name = "", description = "Prompt with empty name")
			public Mono<GetPromptResult> emptyNameMethod() {
				return Mono.just(new GetPromptResult("Empty name prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Empty name prompt content")))));
			}

		}

		EmptyNamePrompt promptObject = new EmptyNamePrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("emptyNameMethod");
		assertThat(promptSpecs.get(0).prompt().description()).isEqualTo("Prompt with empty name");
	}

	@Test
	void testGetPromptSpecificationsFiltersOutNonReactiveReturnTypes() {
		class MixedReturnPrompt {

			@McpPrompt(name = "sync-prompt", description = "Synchronous prompt")
			public GetPromptResult syncPrompt() {
				return new GetPromptResult("Sync prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Sync prompt content"))));
			}

			@McpPrompt(name = "async-prompt", description = "Asynchronous prompt")
			public Mono<GetPromptResult> asyncPrompt() {
				return Mono.just(new GetPromptResult("Async prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Async prompt content")))));
			}

		}

		MixedReturnPrompt promptObject = new MixedReturnPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("async-prompt");
		assertThat(promptSpecs.get(0).prompt().description()).isEqualTo("Asynchronous prompt");
	}

	@Test
	void testGetPromptSpecificationsWithMultiplePromptMethods() {
		class MultiplePromptMethods {

			@McpPrompt(name = "prompt1", description = "First prompt")
			public Mono<GetPromptResult> firstPrompt() {
				return Mono.just(new GetPromptResult("First prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("First prompt content")))));
			}

			@McpPrompt(name = "prompt2", description = "Second prompt")
			public Mono<GetPromptResult> secondPrompt() {
				return Mono.just(new GetPromptResult("Second prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Second prompt content")))));
			}

		}

		MultiplePromptMethods promptObject = new MultiplePromptMethods();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(2);
		assertThat(promptSpecs.get(0).prompt().name()).isIn("prompt1", "prompt2");
		assertThat(promptSpecs.get(1).prompt().name()).isIn("prompt1", "prompt2");
		assertThat(promptSpecs.get(0).prompt().name()).isNotEqualTo(promptSpecs.get(1).prompt().name());
	}

	@Test
	void testGetPromptSpecificationsWithMultiplePromptObjects() {
		class FirstPromptObject {

			@McpPrompt(name = "first-prompt", description = "First prompt")
			public Mono<GetPromptResult> firstPrompt() {
				return Mono.just(new GetPromptResult("First prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("First prompt content")))));
			}

		}

		class SecondPromptObject {

			@McpPrompt(name = "second-prompt", description = "Second prompt")
			public Mono<GetPromptResult> secondPrompt() {
				return Mono.just(new GetPromptResult("Second prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Second prompt content")))));
			}

		}

		FirstPromptObject firstObject = new FirstPromptObject();
		SecondPromptObject secondObject = new SecondPromptObject();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(firstObject, secondObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(2);
		assertThat(promptSpecs.get(0).prompt().name()).isIn("first-prompt", "second-prompt");
		assertThat(promptSpecs.get(1).prompt().name()).isIn("first-prompt", "second-prompt");
		assertThat(promptSpecs.get(0).prompt().name()).isNotEqualTo(promptSpecs.get(1).prompt().name());
	}

	@Test
	void testGetPromptSpecificationsWithMixedMethods() {
		class MixedMethods {

			@McpPrompt(name = "valid-prompt", description = "Valid prompt")
			public Mono<GetPromptResult> validPrompt() {
				return Mono.just(new GetPromptResult("Valid prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Valid prompt content")))));
			}

			public GetPromptResult nonAnnotatedMethod() {
				return new GetPromptResult("Non-annotated result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Non-annotated content"))));
			}

			@McpPrompt(name = "sync-prompt", description = "Sync prompt")
			public GetPromptResult syncPrompt() {
				return new GetPromptResult("Sync prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Sync prompt content"))));
			}

		}

		MixedMethods promptObject = new MixedMethods();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("valid-prompt");
		assertThat(promptSpecs.get(0).prompt().description()).isEqualTo("Valid prompt");
	}

	@Test
	void testGetPromptSpecificationsWithArguments() {
		class ArgumentPrompt {

			@McpPrompt(name = "argument-prompt", description = "Prompt with arguments")
			public Mono<GetPromptResult> argumentPrompt(
					@McpArg(name = "name", description = "User's name", required = true) String name,
					@McpArg(name = "age", description = "User's age", required = false) Integer age) {
				return Mono.just(new GetPromptResult("Argument prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent(
								"Hello " + name + ", you are " + (age != null ? age : "unknown") + " years old")))));
			}

		}

		ArgumentPrompt promptObject = new ArgumentPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("argument-prompt");
		assertThat(promptSpecs.get(0).prompt().arguments()).hasSize(2);

		// Test that the handler works with arguments
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		args.put("name", "John");
		args.put("age", 30);
		GetPromptRequest request = new GetPromptRequest("argument-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.description()).isEqualTo("Argument prompt result");
			assertThat(promptResult.messages()).hasSize(1);
			PromptMessage message = promptResult.messages().get(0);
			assertThat(message.role()).isEqualTo(Role.ASSISTANT);
			assertThat(((TextContent) message.content()).text()).isEqualTo("Hello John, you are 30 years old");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithPrivateMethod() {
		class PrivateMethodPrompt {

			@McpPrompt(name = "private-prompt", description = "Private prompt method")
			private Mono<GetPromptResult> privatePrompt() {
				return Mono.just(new GetPromptResult("Private prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Private prompt content")))));
			}

		}

		PrivateMethodPrompt promptObject = new PrivateMethodPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("private-prompt");
		assertThat(promptSpecs.get(0).prompt().description()).isEqualTo("Private prompt method");

		// Test that the handler works with private methods
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("private-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.description()).isEqualTo("Private prompt result");
			assertThat(promptResult.messages()).hasSize(1);
			PromptMessage message = promptResult.messages().get(0);
			assertThat(message.role()).isEqualTo(Role.ASSISTANT);
			assertThat(((TextContent) message.content()).text()).isEqualTo("Private prompt content");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithMonoStringReturn() {
		class MonoStringReturnPrompt {

			@McpPrompt(name = "mono-string-prompt", description = "Prompt returning Mono<String>")
			public Mono<String> monoStringPrompt() {
				return Mono.just("Simple string response");
			}

		}

		MonoStringReturnPrompt promptObject = new MonoStringReturnPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("mono-string-prompt");

		// Test that the handler works with Mono<String> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("mono-string-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.messages()).hasSize(1);
			PromptMessage message = promptResult.messages().get(0);
			assertThat(message.role()).isEqualTo(Role.ASSISTANT);
			assertThat(((TextContent) message.content()).text()).isEqualTo("Simple string response");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithExchangeParameter() {
		class ExchangeParameterPrompt {

			@McpPrompt(name = "exchange-prompt", description = "Prompt with exchange parameter")
			public Mono<GetPromptResult> exchangePrompt(McpAsyncServerExchange exchange, GetPromptRequest request) {
				return Mono.just(new GetPromptResult("Exchange prompt result",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent("Prompt with exchange: "
								+ (exchange != null ? "present" : "null") + ", name: " + request.name())))));
			}

		}

		ExchangeParameterPrompt promptObject = new ExchangeParameterPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("exchange-prompt");

		// Test that the handler works with exchange parameter
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("exchange-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.description()).isEqualTo("Exchange prompt result");
			assertThat(promptResult.messages()).hasSize(1);
			PromptMessage message = promptResult.messages().get(0);
			assertThat(message.role()).isEqualTo(Role.ASSISTANT);
			assertThat(((TextContent) message.content()).text())
				.isEqualTo("Prompt with exchange: present, name: exchange-prompt");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithRequestParameter() {
		class RequestParameterPrompt {

			@McpPrompt(name = "request-prompt", description = "Prompt with request parameter")
			public Mono<GetPromptResult> requestPrompt(GetPromptRequest request) {
				return Mono.just(new GetPromptResult("Request prompt result", List
					.of(new PromptMessage(Role.ASSISTANT, new TextContent("Prompt for name: " + request.name())))));
			}

		}

		RequestParameterPrompt promptObject = new RequestParameterPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("request-prompt");

		// Test that the handler works with request parameter
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("request-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.description()).isEqualTo("Request prompt result");
			assertThat(promptResult.messages()).hasSize(1);
			PromptMessage message = promptResult.messages().get(0);
			assertThat(message.role()).isEqualTo(Role.ASSISTANT);
			assertThat(((TextContent) message.content()).text()).isEqualTo("Prompt for name: request-prompt");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithMonoMessagesList() {
		class MonoMessagesListPrompt {

			@McpPrompt(name = "mono-messages-list-prompt", description = "Prompt returning Mono<List<PromptMessage>>")
			public Mono<List<PromptMessage>> monoMessagesListPrompt() {
				return Mono.just(List.of(new PromptMessage(Role.ASSISTANT, new TextContent("First message")),
						new PromptMessage(Role.ASSISTANT, new TextContent("Second message"))));
			}

		}

		MonoMessagesListPrompt promptObject = new MonoMessagesListPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("mono-messages-list-prompt");

		// Test that the handler works with Mono<List<PromptMessage>> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("mono-messages-list-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.messages()).hasSize(2);
			assertThat(((TextContent) promptResult.messages().get(0).content()).text()).isEqualTo("First message");
			assertThat(((TextContent) promptResult.messages().get(1).content()).text()).isEqualTo("Second message");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithMonoSingleMessage() {
		class MonoSingleMessagePrompt {

			@McpPrompt(name = "mono-single-message-prompt", description = "Prompt returning Mono<PromptMessage>")
			public Mono<PromptMessage> monoSingleMessagePrompt() {
				return Mono.just(new PromptMessage(Role.ASSISTANT, new TextContent("Single message")));
			}

		}

		MonoSingleMessagePrompt promptObject = new MonoSingleMessagePrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("mono-single-message-prompt");

		// Test that the handler works with Mono<PromptMessage> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("mono-single-message-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.messages()).hasSize(1);
			assertThat(((TextContent) promptResult.messages().get(0).content()).text()).isEqualTo("Single message");
		}).verifyComplete();
	}

	@Test
	void testGetPromptSpecificationsWithMonoStringList() {
		class MonoStringListPrompt {

			@McpPrompt(name = "mono-string-list-prompt", description = "Prompt returning Mono<List<String>>")
			public Mono<List<String>> monoStringListPrompt() {
				return Mono.just(List.of("First string", "Second string", "Third string"));
			}

		}

		MonoStringListPrompt promptObject = new MonoStringListPrompt();
		AsyncMcpPromptProvider provider = new AsyncMcpPromptProvider(List.of(promptObject));

		List<AsyncPromptSpecification> promptSpecs = provider.getPromptSpecifications();

		assertThat(promptSpecs).hasSize(1);
		assertThat(promptSpecs.get(0).prompt().name()).isEqualTo("mono-string-list-prompt");

		// Test that the handler works with Mono<List<String>> return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		Map<String, Object> args = new HashMap<>();
		GetPromptRequest request = new GetPromptRequest("mono-string-list-prompt", args);
		Mono<GetPromptResult> result = promptSpecs.get(0).promptHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(promptResult -> {
			assertThat(promptResult.messages()).hasSize(3);
			assertThat(((TextContent) promptResult.messages().get(0).content()).text()).isEqualTo("First string");
			assertThat(((TextContent) promptResult.messages().get(1).content()).text()).isEqualTo("Second string");
			assertThat(((TextContent) promptResult.messages().get(2).content()).text()).isEqualTo("Third string");
		}).verifyComplete();
	}

}
