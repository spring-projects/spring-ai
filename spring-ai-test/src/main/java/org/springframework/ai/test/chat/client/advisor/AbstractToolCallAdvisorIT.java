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

package org.springframework.ai.test.chat.client.advisor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for {@link ToolCallAdvisor} integration tests. Provides reusable
 * test scenarios for different ChatModel implementations.
 *
 * <p>
 * Subclasses must implement {@link #getChatModel()} to provide the specific ChatModel
 * instance to test against.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractToolCallAdvisorIT {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Returns the ChatModel instance to be used in tests.
	 * @return the ChatModel to test
	 */
	protected abstract ChatModel getChatModel();

	/**
	 * Creates the weather tool callback used in tests. Subclasses can override this to
	 * provide a custom tool callback.
	 * @return the tool callback for weather service
	 */
	protected ToolCallback createWeatherToolCallback() {
		return FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
			.description("Get the weather in location")
			.inputType(MockWeatherService.Request.class)
			.build();
	}

	/**
	 * Creates a weather tool callback with returnDirect=true.
	 */
	protected ToolCallback createReturnDirectWeatherToolCallback() {
		return FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
			.description("Get the weather in location")
			.inputType(MockWeatherService.Request.class)
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.build();
	}

	@Nested
	class CallTests {

		@Test
		void callMultipleToolInvocations() {

			String response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(ToolCallAdvisor.builder().build())
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?"))
				.toolCallbacks(createWeatherToolCallback())
				.call()
				.content();

			logger.info("Response: {}", response);

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void callMultipleToolInvocationsWithExternalMemory() {

			var response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(ToolCallAdvisor.builder().disableInternalConversationHistory().build(),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
							.build())
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?"))
				.toolCallbacks(createWeatherToolCallback())
				.call()
				.content();

			logger.info("Response: {}", response);

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void callDefaultAdvisorConfiguration() {

			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(ToolCallAdvisor.builder().build())
				.build();

			String response = chatClient.prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
				.toolCallbacks(createWeatherToolCallback())
				.call()
				.content();

			logger.info("Response: {}", response);

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void callDefaultAdvisorConfigurationWithExternalMemory() {

			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(ToolCallAdvisor.builder().disableInternalConversationHistory().build(),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
				.build();

			String response = chatClient.prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
				.toolCallbacks(createWeatherToolCallback())
				.call()
				.content();

			logger.info("Response: {}", response);

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void callWithReturnDirect() {
			String response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(ToolCallAdvisor.builder().build())
				.user("What's the weather like in Tokyo?")
				.toolCallbacks(createReturnDirectWeatherToolCallback())
				.call()
				.content();

			logger.info("Response: {}", response);

			// With returnDirect=true, the raw tool result is returned without LLM
			// processing
			assertThat(response).contains("temp");
		}

	}

	@Nested
	class StreamTests {

		@Test
		void streamMultipleToolInvocations() {

			Flux<String> response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(ToolCallAdvisor.builder().build())
				.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
				.toolCallbacks(createWeatherToolCallback())
				.stream()
				.content();

			List<String> chunks = response.collectList().block();
			String content = Objects.requireNonNull(chunks).stream().collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).contains("30", "10", "15");
		}

		@Test
		void streamMultipleToolInvocationsWithExternalMemory() {

			Flux<String> response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(ToolCallAdvisor.builder().disableInternalConversationHistory().build(),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
							.build())
				.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
				.toolCallbacks(createWeatherToolCallback())
				.stream()
				.content();

			List<String> chunks = response.collectList().block();
			String content = Objects.requireNonNull(chunks).stream().collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).contains("30", "10", "15");
		}

		@Test
		void streamDefaultAdvisorConfiguration() {

			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(ToolCallAdvisor.builder().build())
				.build();

			Flux<String> response = chatClient.prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
				.toolCallbacks(createWeatherToolCallback())
				.stream()
				.content();

			List<String> chunks = response.collectList().block();
			String content = Objects.requireNonNull(chunks).stream().collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).contains("30", "10", "15");
		}

		@Test
		void streamDefaultAdvisorConfigurationWithExternalMemory() {

			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(ToolCallAdvisor.builder().disableInternalConversationHistory().build(),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
				.build();

			Flux<String> response = chatClient.prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris in Celsius?")
				.toolCallbacks(createWeatherToolCallback())
				.stream()
				.content();

			List<String> chunks = response.collectList().block();
			String content = Objects.requireNonNull(chunks).stream().collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).contains("30", "10", "15");
		}

		@Test
		void streamWithReturnDirect() {
			Flux<String> response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(ToolCallAdvisor.builder().build())
				.user("What's the weather like in Tokyo?")
				.toolCallbacks(createReturnDirectWeatherToolCallback())
				.stream()
				.content();

			List<String> chunks = response.collectList().block();
			String content = Objects.requireNonNull(chunks).stream().collect(Collectors.joining());
			logger.info("Response: {}", content);

			// With returnDirect=true, the raw tool result is returned without LLM
			// processing
			assertThat(content).contains("temp");
		}

	}

}
