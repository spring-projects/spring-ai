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

package org.springframework.ai.test.chat.client.advisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract integration-test base for {@link ToolCallingAdvisor} auto-registration
 * behaviour.
 *
 * <p>
 * Covers functional correctness (do the right temperatures come back?) and structural
 * correctness (is {@code ToolCallingAdvisor} actually in control of the loop?). The
 * structural proof uses a {@link ChainIterationCountingAdvisor} positioned just after the
 * auto-registered {@code ToolCallingAdvisor}. Because the advisor recurses via
 * {@code chain.copy(this).nextCall()}, the counting advisor is invoked once per tool-call
 * iteration plus once for the final answer — a count &gt; 1 proves the advisor chain owns
 * the loop. When only the model's built-in path runs the count is always 1.
 *
 * <p>
 * Subclasses provide the {@link ChatModel} implementation via {@link #getChatModel()}.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractToolCallingAdvisorAutoRegistrationIT {

	protected abstract ChatModel getChatModel();

	protected ToolCallback createWeatherToolCallback() {
		return FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
			.description("Get the weather in location")
			.inputType(MockWeatherService.Request.class)
			.build();
	}

	protected ToolCallback createReturnDirectWeatherToolCallback() {
		return FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
			.description("Get the weather in location")
			.inputType(MockWeatherService.Request.class)
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.build();
	}

	private static String collect(Flux<String> flux) {
		return Objects.requireNonNull(flux.collectList().block()).stream().collect(Collectors.joining());
	}

	// -------------------------------------------------------------------------
	// Call path: functional
	// -------------------------------------------------------------------------

	@Nested
	class CallFunctional {

		@Test
		void autoRegisteredAdvisorExecutesTools() {
			String response = ChatClient.create(getChatModel())
				.prompt()
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void autoRegisteredAdvisorWithDownstreamMemory() {
			String response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
					.build())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "auto-register-with-memory"))
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void defaultAdvisorsWithAutoRegistration() {
			var chatClient = ChatClient.builder(getChatModel()).build();

			String response = chatClient.prompt()
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void defaultAdvisorsWithMemoryAndAutoRegistration() {
			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
				.build();

			String response = chatClient.prompt()
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "default-advisors-memory"))
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			assertThat(response).contains("30", "10", "15");
		}

		@Test
		void returnDirectWithAutoRegistration() {
			String response = ChatClient.create(getChatModel())
				.prompt()
				.user("What's the weather in Tokyo?")
				.tools(createReturnDirectWeatherToolCallback())
				.call()
				.content();

			assertThat(response).contains("temp");
		}

	}

	// -------------------------------------------------------------------------
	// Call path: structural (prove ToolCallAdvisor is in control)
	// -------------------------------------------------------------------------

	@Nested
	class CallStructural {

		@Test
		void advisorChainLoopsWhenAutoRegistered() {
			var counter = new ChainIterationCountingAdvisor();

			ChatClient.create(getChatModel())
				.prompt()
				.advisors(counter)
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			// chain.copy(this) restarts after ToolCallAdvisor on every iteration
			assertThat(counter.getCallCount())
				.as("ToolCallAdvisor should loop: at least one tool iteration + final answer")
				.isGreaterThanOrEqualTo(2);
		}

		@Test
		void advisorChainDoesNotLoopWhenAutoRegisterDisabled() {
			var counter = new ChainIterationCountingAdvisor();

			ChatClient.create(getChatModel())
				.prompt()
				.advisors(counter)
				.advisors(a -> a.param(ChatClientAttributes.TOOL_CALL_ADVISOR_AUTO_REGISTER.getKey(), false))
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			// Model handles tools internally; chain traversed exactly once
			assertThat(counter.getCallCount()).as("Without ToolCallAdvisor the chain should be traversed exactly once")
				.isEqualTo(1);
		}

		@Test
		void toolResponseMessagesVisibleInChainWhenAutoRegistered() {
			var counter = new ChainIterationCountingAdvisor();

			ChatClient.create(getChatModel())
				.prompt()
				.advisors(counter)
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			// On the second iteration the request must contain tool-response messages
			// proving they are visible to every advisor after ToolCallAdvisor.
			List<ChatClientRequest> requests = counter.getCapturedRequests();
			assertThat(requests).hasSizeGreaterThanOrEqualTo(2);

			boolean toolResponseVisible = requests.stream()
				.skip(1) // skip first (no tool results yet)
				.anyMatch(req -> req.prompt()
					.getInstructions()
					.stream()
					.anyMatch(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage));

			assertThat(toolResponseVisible)
				.as("Tool response messages must be visible in the advisor chain on subsequent iterations")
				.isTrue();
		}

	}

	// -------------------------------------------------------------------------
	// Stream path: functional
	// -------------------------------------------------------------------------

	@Nested
	class StreamFunctional {

		@Test
		void autoRegisteredAdvisorExecutesTools() {
			String content = collect(ChatClient.create(getChatModel())
				.prompt()
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.stream()
				.content());

			assertThat(content).contains("30", "10", "15");
		}

		@Test
		void autoRegisteredAdvisorWithDownstreamMemory() {
			String content = collect(ChatClient.create(getChatModel())
				.prompt()
				.advisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
					.build())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "stream-auto-register-with-memory"))
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.stream()
				.content());

			assertThat(content).contains("30", "10", "15");
		}

		@Test
		void returnDirectWithAutoRegistration() {
			String content = collect(ChatClient.create(getChatModel())
				.prompt()
				.user("What's the weather in Tokyo?")
				.tools(createReturnDirectWeatherToolCallback())
				.stream()
				.content());

			assertThat(content).contains("temp");
		}

	}

	// -------------------------------------------------------------------------
	// Stream path: structural
	// -------------------------------------------------------------------------

	@Nested
	class StreamStructural {

		@Test
		void advisorChainLoopsWhenAutoRegistered() {
			var counter = new ChainIterationCountingAdvisor();

			collect(ChatClient.create(getChatModel())
				.prompt()
				.advisors(counter)
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.stream()
				.content());

			assertThat(counter.getCallCount())
				.as("ToolCallAdvisor should loop on stream: at least one tool iteration + final answer")
				.isGreaterThanOrEqualTo(2);
		}

		@Test
		void advisorChainDoesNotLoopWhenAutoRegisterDisabled() {
			var counter = new ChainIterationCountingAdvisor();

			collect(ChatClient.create(getChatModel())
				.prompt()
				.advisors(counter)
				.advisors(a -> a.param(ChatClientAttributes.TOOL_CALL_ADVISOR_AUTO_REGISTER.getKey(), false))
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.stream()
				.content());

			assertThat(counter.getCallCount())
				.as("Without ToolCallAdvisor the stream chain should be traversed exactly once")
				.isEqualTo(1);
		}

	}

	// -------------------------------------------------------------------------
	// AdvisorParams API
	// -------------------------------------------------------------------------

	@Nested
	class AdvisorParamsApi {

		@Test
		void toolCallingAdvisorAutoRegisterFalseViaFactory() {
			var counter = new ChainIterationCountingAdvisor();

			ChatClient.create(getChatModel())
				.prompt()
				.advisors(counter)
				.advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
				.user("What's the weather in San Francisco, Tokyo, and Paris in Celsius?")
				.tools(createWeatherToolCallback())
				.call()
				.content();

			assertThat(counter.getCallCount()).isEqualTo(1);
		}

	}

	// -------------------------------------------------------------------------
	// Inner types (InnerTypeLast)
	// -------------------------------------------------------------------------

	/**
	 * Counts how many times the advisor chain passes through this advisor. Positioned
	 * just after {@link ToolCallingAdvisor} (order = DEFAULT_ORDER + 100), it is invoked
	 * once per recursive tool-call iteration when the advisor manages the loop, and
	 * exactly once when the model handles tool calls internally.
	 */
	protected static class ChainIterationCountingAdvisor implements BaseAdvisor {

		private final AtomicInteger callCount = new AtomicInteger();

		private final List<ChatClientRequest> capturedRequests = new ArrayList<>();

		private final int order;

		public ChainIterationCountingAdvisor() {
			this(ToolCallingAdvisor.DEFAULT_ORDER + 100);
		}

		public ChainIterationCountingAdvisor(int order) {
			this.order = order;
		}

		@Override
		public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
			this.callCount.incrementAndGet();
			this.capturedRequests.add(request);
			return request;
		}

		@Override
		public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
			return response;
		}

		@Override
		public int getOrder() {
			return this.order;
		}

		public int getCallCount() {
			return this.callCount.get();
		}

		public List<ChatClientRequest> getCapturedRequests() {
			return Collections.unmodifiableList(this.capturedRequests);
		}

	}

}
