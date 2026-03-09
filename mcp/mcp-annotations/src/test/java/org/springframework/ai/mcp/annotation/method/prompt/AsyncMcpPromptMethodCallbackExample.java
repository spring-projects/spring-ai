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

package org.springframework.ai.mcp.annotation.method.prompt;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.adapter.PromptAdapter;

/**
 * Example demonstrating how to use the AsyncMcpPromptMethodCallback.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpPromptMethodCallbackExample {

	private AsyncMcpPromptMethodCallbackExample() {
	}

	/**
	 * Example of how to create and use an AsyncMcpPromptMethodCallback.
	 */
	public static void main(String[] args) throws Exception {
		// Create an instance of the prompt provider
		AsyncPromptProvider provider = new AsyncPromptProvider();

		// Example 1: Using a method that returns Mono<GetPromptResult>
		System.out.println("Example 1: Method returning Mono<GetPromptResult>");
		demonstrateAsyncGreetingPrompt(provider);

		// Example 2: Using a method that returns Mono<String>
		System.out.println("\nExample 2: Method returning Mono<String>");
		demonstrateAsyncStringPrompt(provider);

		// Example 3: Using a method that returns Mono<List<String>>
		System.out.println("\nExample 3: Method returning Mono<List<String>>");
		demonstrateAsyncStringListPrompt(provider);
	}

	/**
	 * Demonstrates using a method that returns Mono<GetPromptResult>.
	 */
	private static void demonstrateAsyncGreetingPrompt(AsyncPromptProvider provider) throws Exception {
		// Get the method for the async greeting prompt
		Method asyncGreetingMethod = AsyncPromptProvider.class.getMethod("asyncGreetingPrompt", String.class);

		// Get the McpPrompt annotation from the method
		McpPrompt promptAnnotation = asyncGreetingMethod.getAnnotation(McpPrompt.class);

		// Convert the annotation to a Prompt object with argument information
		Prompt prompt = PromptAdapter.asPrompt(promptAnnotation, asyncGreetingMethod);

		// Create the callback
		BiFunction<McpAsyncServerExchange, GetPromptRequest, Mono<GetPromptResult>> callback = AsyncMcpPromptMethodCallback
			.builder()
			.method(asyncGreetingMethod)
			.bean(provider)
			.prompt(prompt)
			.build();

		// Create a request with arguments
		Map<String, Object> requestArgs = Map.of("name", "John");
		GetPromptRequest request = new GetPromptRequest("async-greeting", requestArgs);

		// Apply the callback (in a real application, you would have a real exchange)
		Mono<GetPromptResult> resultMono = callback.apply(null, request);

		// Subscribe to the result
		resultMono.subscribe(result -> {
			System.out.println("Description: " + result.description());
			System.out.println("Messages:");
			for (PromptMessage message : result.messages()) {
				System.out.println("  Role: " + message.role());
				if (message.content() instanceof TextContent) {
					System.out.println("  Content: " + ((TextContent) message.content()).text());
				}
			}
		});

		// Wait a bit for the subscription to complete
		Thread.sleep(500);
	}

	/**
	 * Demonstrates using a method that returns Mono<String>.
	 */
	private static void demonstrateAsyncStringPrompt(AsyncPromptProvider provider) throws Exception {
		// Get the method for the async string prompt
		Method asyncStringMethod = AsyncPromptProvider.class.getMethod("asyncStringPrompt", GetPromptRequest.class);

		// Get the McpPrompt annotation from the method
		McpPrompt promptAnnotation = asyncStringMethod.getAnnotation(McpPrompt.class);

		// Convert the annotation to a Prompt object with argument information
		Prompt prompt = PromptAdapter.asPrompt(promptAnnotation, asyncStringMethod);

		// Create the callback
		BiFunction<McpAsyncServerExchange, GetPromptRequest, Mono<GetPromptResult>> callback = AsyncMcpPromptMethodCallback
			.builder()
			.method(asyncStringMethod)
			.bean(provider)
			.prompt(prompt)
			.build();

		// Create a request with arguments
		Map<String, Object> requestArgs = Map.of("name", "Alice");
		GetPromptRequest request = new GetPromptRequest("async-string", requestArgs);

		// Apply the callback
		Mono<GetPromptResult> resultMono = callback.apply(null, request);

		// Subscribe to the result
		resultMono.subscribe(result -> {
			System.out.println("Messages:");
			for (PromptMessage message : result.messages()) {
				System.out.println("  Role: " + message.role());
				if (message.content() instanceof TextContent) {
					System.out.println("  Content: " + ((TextContent) message.content()).text());
				}
			}
		});

		// Wait a bit for the subscription to complete
		Thread.sleep(500);
	}

	/**
	 * Demonstrates using a method that returns Mono<List<String>>.
	 */
	private static void demonstrateAsyncStringListPrompt(AsyncPromptProvider provider) throws Exception {
		// Get the method for the async string list prompt
		Method asyncStringListMethod = AsyncPromptProvider.class.getMethod("asyncStringListPrompt", String.class);

		// Get the McpPrompt annotation from the method
		McpPrompt promptAnnotation = asyncStringListMethod.getAnnotation(McpPrompt.class);

		// Convert the annotation to a Prompt object with argument information
		Prompt prompt = PromptAdapter.asPrompt(promptAnnotation, asyncStringListMethod);

		// Create the callback
		BiFunction<McpAsyncServerExchange, GetPromptRequest, Mono<GetPromptResult>> callback = AsyncMcpPromptMethodCallback
			.builder()
			.method(asyncStringListMethod)
			.bean(provider)
			.prompt(prompt)
			.build();

		// Create a request with arguments
		Map<String, Object> requestArgs = Map.of("topic", "MCP");
		GetPromptRequest request = new GetPromptRequest("async-string-list", requestArgs);

		// Apply the callback
		Mono<GetPromptResult> resultMono = callback.apply(null, request);

		// Subscribe to the result
		resultMono.subscribe(result -> {
			System.out.println("Messages:");
			for (PromptMessage message : result.messages()) {
				System.out.println("  Role: " + message.role());
				if (message.content() instanceof TextContent) {
					System.out.println("  Content: " + ((TextContent) message.content()).text());
				}
			}
		});

		// Wait a bit for the subscription to complete
		Thread.sleep(500);
	}

	/**
	 * A class that provides prompt methods with asynchronous processing.
	 */
	public static class AsyncPromptProvider {

		/**
		 * A simple greeting prompt that takes a name parameter and returns a Mono.
		 * @param name The name to greet
		 * @return A Mono that emits a greeting message
		 */
		@McpPrompt(name = "async-greeting", description = "An asynchronous greeting prompt")
		public Mono<GetPromptResult> asyncGreetingPrompt(
				@McpArg(name = "name", description = "The name to greet", required = true) String name) {
			// Simulate some asynchronous processing
			return Mono.delay(Duration.ofMillis(100))
				.map(ignored -> new GetPromptResult("Async Greeting", List.of(new PromptMessage(Role.ASSISTANT,
						new TextContent("Hello, " + name + "! Welcome to the MCP system. (async)")))));
		}

		/**
		 * A prompt that returns a Mono<String>.
		 * @param request The prompt request
		 * @return A Mono that emits a string
		 */
		@McpPrompt(name = "async-string", description = "A prompt returning a Mono<String>")
		public Mono<String> asyncStringPrompt(GetPromptRequest request) {
			// Simulate some asynchronous processing
			return Mono.delay(Duration.ofMillis(100)).map(ignored -> "Async string response for " + request.name());
		}

		/**
		 * A prompt that returns a Mono<PromptMessage>.
		 * @param request The prompt request
		 * @return A Mono that emits a prompt message
		 */
		@McpPrompt(name = "async-message", description = "A prompt returning a Mono<PromptMessage>")
		public Mono<PromptMessage> asyncMessagePrompt(GetPromptRequest request) {
			// Simulate some asynchronous processing
			return Mono.delay(Duration.ofMillis(100))
				.map(ignored -> new PromptMessage(Role.ASSISTANT,
						new TextContent("Async single message for " + request.name())));
		}

		/**
		 * A prompt that returns a Mono<List<PromptMessage>>.
		 * @param request The prompt request
		 * @return A Mono that emits a list of prompt messages
		 */
		@McpPrompt(name = "async-message-list", description = "A prompt returning a Mono<List<PromptMessage>>")
		public Mono<List<PromptMessage>> asyncMessageListPrompt(GetPromptRequest request) {
			// Simulate some asynchronous processing
			return Mono.delay(Duration.ofMillis(100))
				.map(ignored -> List.of(
						new PromptMessage(Role.ASSISTANT, new TextContent("Async message 1 for " + request.name())),
						new PromptMessage(Role.ASSISTANT, new TextContent("Async message 2 for " + request.name()))));
		}

		/**
		 * A prompt that returns a Mono<List<String>>.
		 * @param topic The topic to provide information about
		 * @return A Mono that emits a list of strings with information about the topic
		 */
		@McpPrompt(name = "async-string-list", description = "A prompt returning a Mono<List<String>>")
		public Mono<List<String>> asyncStringListPrompt(@McpArg(name = "topic",
				description = "The topic to provide information about", required = true) String topic) {
			// Simulate some asynchronous processing
			return Mono.delay(Duration.ofMillis(100)).map(ignored -> {
				if ("MCP".equalsIgnoreCase(topic)) {
					return List.of(
							"The Model Context Protocol (MCP) is a standardized way for servers to communicate with language models. (async)",
							"It provides a structured approach for exchanging information, making requests, and handling responses. (async)",
							"MCP allows servers to expose resources, tools, and prompts to clients in a consistent way. (async)");
				}
				else {
					return List.of("I don't have specific information about " + topic + ". (async)",
							"Please try a different topic or ask a more specific question. (async)");
				}
			});
		}

		/**
		 * A more complex prompt that generates a personalized message asynchronously.
		 * @param exchange The server exchange
		 * @param name The user's name
		 * @param age The user's age
		 * @param interests The user's interests
		 * @return A Mono that emits a personalized message
		 */
		@McpPrompt(name = "async-personalized-message",
				description = "Generates a personalized message based on user information asynchronously")
		public Mono<GetPromptResult> asyncPersonalizedMessage(McpAsyncServerExchange exchange,
				@McpArg(name = "name", description = "The user's name", required = true) String name,
				@McpArg(name = "age", description = "The user's age", required = false) Integer age,
				@McpArg(name = "interests", description = "The user's interests", required = false) String interests) {

			// Simulate some asynchronous processing
			return Mono.delay(Duration.ofMillis(100)).map(ignored -> {
				StringBuilder message = new StringBuilder();
				message.append("Hello, ").append(name).append("! (async)\n\n");

				if (age != null) {
					message.append("At ").append(age).append(" years old, you have ");
					if (age < 30) {
						message.append("so much ahead of you. (async)\n\n");
					}
					else if (age < 60) {
						message.append("gained valuable life experience. (async)\n\n");
					}
					else {
						message.append("accumulated wisdom to share with others. (async)\n\n");
					}
				}

				if (interests != null && !interests.isEmpty()) {
					message.append("Your interest in ")
						.append(interests)
						.append(" shows your curiosity and passion for learning. (async)\n\n");
				}

				message.append(
						"I'm here to assist you with any questions you might have about the Model Context Protocol. (async)");

				return new GetPromptResult("Async Personalized Message",
						List.of(new PromptMessage(Role.ASSISTANT, new TextContent(message.toString()))));
			});
		}

	}

}
