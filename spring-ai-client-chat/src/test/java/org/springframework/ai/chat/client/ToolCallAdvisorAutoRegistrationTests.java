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

package org.springframework.ai.chat.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.ToolAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.DefaultToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ToolCallAdvisor} is auto-registered (or not) by
 * {@link DefaultChatClient} and that the correct tool-call execution path is taken.
 *
 * <p>
 * The key structural signal is a {@link ChainIterationCountingAdvisor} placed just after
 * the {@link ToolCallAdvisor} in the chain (order = {@code DEFAULT_ORDER + 100}). When
 * {@code ToolCallAdvisor} drives the loop, every iteration calls
 * {@code chain.copy(this).nextCall()}, which invokes the counting advisor once per
 * iteration. The built-in model path calls through the chain only once.
 *
 * <p>
 * A secondary signal is the {@code internalToolExecutionEnabled} flag on the
 * {@link Prompt} options: {@code ToolCallAdvisor} always sets it to {@code false} before
 * forwarding to the model, proving it — not the model — owns the tool-call lifecycle.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
@ExtendWith(MockitoExtension.class)
class ToolCallAdvisorAutoRegistrationTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	ToolCallback weatherTool;

	@BeforeEach
	void setup() {
		lenient().when(this.chatModel.getOptions()).thenReturn(DefaultToolCallingChatOptions.builder().build());
		this.weatherTool = FunctionToolCallback.builder("getWeather", (CityInput in) -> in.city() + ": 25C")
			.description("Get weather for a city")
			.inputType(CityInput.class)
			.build();
	}

	// -------------------------------------------------------------------------
	// Mock stubs (must be before inner types per checkstyle InnerTypeLast)
	// -------------------------------------------------------------------------

	void stubTwoCallCycle() {
		when(this.chatModel.call(any(Prompt.class))).thenReturn(toolCallChatResponse()).thenReturn(finalChatResponse());
	}

	void stubSingleCallCycle() {
		when(this.chatModel.call(any(Prompt.class))).thenReturn(finalChatResponse());
	}

	void stubTwoStreamCallCycle() {
		when(this.chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(toolCallChatResponse()))
			.thenReturn(Flux.just(finalChatResponse()));
	}

	void stubSingleStreamCallCycle() {
		when(this.chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(finalChatResponse()));
	}

	ChatResponse toolCallChatResponse() {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-1", "function", "getWeather",
				"{\"city\":\"Paris\"}");
		AssistantMessage msg = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
		return chatResponse(msg);
	}

	ChatResponse finalChatResponse() {
		return chatResponse(new AssistantMessage("Paris: 25C"));
	}

	private static ChatResponse chatResponse(AssistantMessage msg) {
		ChatResponseMetadata meta = ChatResponseMetadata.builder().id("").model("").build();
		return ChatResponse.builder().generations(List.of(new Generation(msg))).metadata(meta).build();
	}

	// -------------------------------------------------------------------------
	// Call path tests
	// -------------------------------------------------------------------------

	@Nested
	class CallPath {

		@Test
		void autoRegistersWhenToolsPresent() {
			stubTwoCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			String content = ChatClient.create(chatModel)
				.prompt()
				.advisors(counter)
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.call()
				.content();

			assertThat(content).isNotBlank();
			// ToolCallAdvisor looped: initial call + retry after tool execution
			assertThat(counter.getCallCount()).isGreaterThanOrEqualTo(2);

			// ToolCallAdvisor disables internal execution on every prompt it sends
			verify(chatModel, times(2)).call(promptCaptor.capture());
			promptCaptor.getAllValues()
				.forEach(p -> assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(p.getOptions()))
					.isFalse());
		}

		@Test
		void doesNotAutoRegisterWhenDisabled() {
			stubSingleCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			ChatClient.create(chatModel)
				.prompt()
				.advisors(counter)
				.advisors(a -> a.param(ChatClientAttributes.TOOL_CALL_ADVISOR_AUTO_REGISTER.getKey(), false))
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.call()
				.content();

			// No ToolCallAdvisor loop — chain traversed exactly once
			assertThat(counter.getCallCount()).isEqualTo(1);

			// internalToolExecutionEnabled NOT forced to false — model owns tool
			// execution
			verify(chatModel, times(1)).call(promptCaptor.capture());
			assertThat(ToolCallingChatOptions.isInternalToolExecutionEnabled(promptCaptor.getValue().getOptions()))
				.isTrue();
		}

		@Test
		void doesNotAutoRegisterWhenNoTools() {
			stubSingleCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			ChatClient.create(chatModel).prompt().advisors(counter).user("hello").call().content();

			assertThat(counter.getCallCount()).isEqualTo(1);
			verify(chatModel, times(1)).call(any(Prompt.class));
		}

		@Test
		void autoRegistersWhenToolsInModelOptions() {
			stubTwoCallCycle();
			// Override default: model already has the weather tool baked into its options
			when(chatModel.getOptions())
				.thenReturn(DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(weatherTool)).build());

			var counter = new ChainIterationCountingAdvisor();
			// No tools added via ChatClient API — tools come from the model's default
			// options
			ChatClient.create(chatModel).prompt().advisors(counter).user("weather?").call().content();

			assertThat(counter.getCallCount()).isGreaterThanOrEqualTo(2);
			verify(chatModel, times(2)).call(any(Prompt.class));
		}

		@Test
		void autoRegistersWhenToolsInOptionsCustomizer() {
			stubTwoCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			// Tools supplied via .options() instead of .toolCallbacks()
			ChatClient.create(chatModel)
				.prompt()
				.advisors(counter)
				.user("weather?")
				.options(DefaultToolCallingChatOptions.builder().toolCallbacks(List.of(weatherTool)))
				.call()
				.content();

			assertThat(counter.getCallCount()).isGreaterThanOrEqualTo(2);
			verify(chatModel, times(2)).call(any(Prompt.class));
		}

		@Test
		void doesNotAutoRegisterWhenExplicitToolCallHandlingAdvisorPresent() {
			stubTwoCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			// Explicit ToolCallAdvisor present — auto-registration must not add a second
			// one
			ChatClient.create(chatModel)
				.prompt()
				.advisors(ToolCallAdvisor.builder().build(), counter)
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.call()
				.content();

			// Explicit advisor controls the loop; model called exactly twice — not four
			assertThat(counter.getCallCount()).isGreaterThanOrEqualTo(2);
			verify(chatModel, times(2)).call(any(Prompt.class));
		}

		@Test
		void autoRegisteredAdvisorUsesInjectedToolCallingManager() {
			ChatResponse first = toolCallChatResponse();
			ChatResponse second = finalChatResponse();
			when(chatModel.call(any(Prompt.class))).thenReturn(first).thenReturn(second);

			var customManager = mock(ToolCallingManager.class);
			when(customManager.executeToolCalls(any(), any())).thenReturn(
					DefaultToolExecutionResult.builder().conversationHistory(List.of()).returnDirect(false).build());

			ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallAdvisor.builder().toolCallingManager(customManager))
				.build()
				.prompt()
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.call()
				.content();

			// The injected manager — not the default — handled the tool call
			verify(customManager).executeToolCalls(any(), any());
			verify(chatModel, times(2)).call(any(Prompt.class));
		}

		@Test
		void throwsWhenMultipleToolAdvisorsRegistered() {
			assertThatThrownBy(() -> ChatClient.create(chatModel)
				.prompt()
				.advisors(ToolCallAdvisor.builder().build(), ToolCallAdvisor.builder().build())
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.call()
				.content()).isInstanceOf(IllegalStateException.class).hasMessageContaining("At most one ToolAdvisor");
		}

		@Test
		void customToolCallHandlingAdvisorPreventsAutoRegistration() {
			stubSingleCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			// Any ToolCallHandlingAdvisor in the chain blocks auto-registration
			ChatClient.create(chatModel)
				.prompt()
				.advisors(new NoOpToolCallHandlingAdvisor(), counter)
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.call()
				.content();

			assertThat(counter.getCallCount()).isEqualTo(1);
			verify(chatModel, times(1)).call(any(Prompt.class));
		}

	}

	// -------------------------------------------------------------------------
	// Stream path tests
	// -------------------------------------------------------------------------

	@Nested
	class StreamPath {

		@Test
		void autoRegistersAndLoopsIterations() {
			stubTwoStreamCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			String content = ChatClient.create(chatModel)
				.prompt()
				.advisors(counter)
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.stream()
				.content()
				.collectList()
				.block()
				.stream()
				.reduce("", String::concat);

			assertThat(content).isNotBlank();
			assertThat(counter.getCallCount()).isGreaterThanOrEqualTo(2);
		}

		@Test
		void doesNotAutoRegisterWhenDisabled() {
			stubSingleStreamCallCycle();

			var counter = new ChainIterationCountingAdvisor();
			ChatClient.create(chatModel)
				.prompt()
				.advisors(counter)
				.advisors(a -> a.param(ChatClientAttributes.TOOL_CALL_ADVISOR_AUTO_REGISTER.getKey(), false))
				.user("weather?")
				.tools(t -> t.callbacks(weatherTool))
				.stream()
				.content()
				.collectList()
				.block();

			assertThat(counter.getCallCount()).isEqualTo(1);
		}

	}

	// -------------------------------------------------------------------------
	// Inner types (must be last per checkstyle InnerTypeLast)
	// -------------------------------------------------------------------------

	/**
	 * Advisor positioned just after {@link ToolCallAdvisor} (order = DEFAULT_ORDER + 100)
	 * that counts invocations. When {@code ToolCallAdvisor} drives the recursive loop,
	 * {@code chain.copy(this)} restarts the chain after it, so this counter increments
	 * once per iteration. When the model handles tool calls internally the chain runs
	 * only once.
	 */
	static class ChainIterationCountingAdvisor implements BaseAdvisor {

		private final AtomicInteger callCount = new AtomicInteger();

		private final List<ChatClientRequest> capturedRequests = new ArrayList<>();

		private final int order;

		ChainIterationCountingAdvisor() {
			this(ToolCallAdvisor.DEFAULT_ORDER + 100);
		}

		ChainIterationCountingAdvisor(int order) {
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

		int getCallCount() {
			return this.callCount.get();
		}

		List<ChatClientRequest> getCapturedRequests() {
			return Collections.unmodifiableList(this.capturedRequests);
		}

	}

	/**
	 * A custom {@link ToolAdvisor} that performs no work. Its presence in the chain is
	 * sufficient to prevent auto-registration of {@link ToolCallAdvisor}.
	 */
	static class NoOpToolCallHandlingAdvisor implements ToolAdvisor, BaseAdvisor {

		@Override
		public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
			return request;
		}

		@Override
		public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
			return response;
		}

		@Override
		public int getOrder() {
			return ToolCallAdvisor.DEFAULT_ORDER;
		}

	}

	record CityInput(String city) {
	}

}
