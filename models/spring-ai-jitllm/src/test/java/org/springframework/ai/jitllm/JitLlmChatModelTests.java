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

package org.springframework.ai.jitllm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.FinishReason;
import org.beehive.jitllm.api.GenerationEvent;
import org.beehive.jitllm.api.GenerationRequest;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.GenerationSession;
import org.beehive.jitllm.api.GenerationTimings;
import org.beehive.jitllm.api.ModelConfiguration;
import org.beehive.jitllm.api.ModelInfo;
import org.beehive.jitllm.api.SessionOptions;
import org.beehive.jitllm.api.TextGenerationModel;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link JitLlmChatModel}, against a scripted engine.
 *
 * @author Michalis Papadimitriou
 */
class JitLlmChatModelTests {

	private final FakeModel engine = new FakeModel();

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private JitLlmChatModel chatModel(JitLlmChatOptions defaults) {
		return new JitLlmChatModel(this.engine, "test-model", false, defaults, ToolCallingManager.builder().build(),
				this.observationRegistry);
	}

	@Test
	void callReturnsTextThinkingUsageAndFinishReason() {
		this.engine.session.script(result("<think>plan</think>\n\nParis.", FinishReason.STOP_TOKEN, List.of()));

		ChatResponse response = chatModel(JitLlmChatOptions.builder().build())
			.call(new Prompt("What is the capital of France?"));

		AssistantMessage message = response.getResult().getOutput();
		assertThat(message.getText()).isEqualTo("Paris.");
		assertThat(message.getMetadata()).containsEntry(JitLlmChatModel.THINKING_METADATA_KEY, "plan");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
		assertThat(response.getMetadata().getModel()).isEqualTo("test-model");
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(7);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(3);
		assertThat(this.observationRegistry).hasObservationWithNameEqualTo("gen_ai.client.operation");
	}

	@Test
	void runtimeOptionsOverrideDefaultsAndReachTheEngine() {
		this.engine.session.script(result("ok", FinishReason.STOP_TOKEN, List.of()));
		JitLlmChatOptions defaults = JitLlmChatOptions.builder()
			.temperature(0.1)
			.topP(0.9)
			.maxTokens(64)
			.seed(7L)
			.build();

		chatModel(defaults)
			.call(new Prompt("hi", ChatOptions.builder().temperature(0.5).stopSequences(List.of("END")).build()));

		GenerationRequest request = this.engine.session.requests.get(0);
		assertThat(request.temperature()).isEqualTo(0.5f);
		assertThat(request.topP()).isEqualTo(0.9f);
		assertThat(request.maxNewTokens()).isEqualTo(64);
		assertThat(request.seed()).isEqualTo(7L);
		assertThat(request.stopSequences()).containsExactly("END");
		assertThat(request.messages()).hasSize(1);
		assertThat(request.messages().get(0).role()).isEqualTo(ChatRole.USER);
	}

	@Test
	void everyRequestResetsTheSessionAndSendsTheWholeConversation() {
		JitLlmChatModel model = chatModel(JitLlmChatOptions.builder().build());
		this.engine.session.script(result("one", FinishReason.STOP_TOKEN, List.of()));
		model.call(new Prompt("first"));
		this.engine.session.script(result("two", FinishReason.STOP_TOKEN, List.of()));
		model.call(
				new Prompt(List.of(new UserMessage("first"), new AssistantMessage("one"), new UserMessage("second"))));

		assertThat(this.engine.sessionsCreated).isEqualTo(1);
		assertThat(this.engine.session.resets).isEqualTo(2);
		assertThat(this.engine.session.requests.get(1).messages()).hasSize(3);
	}

	@Test
	void toolCallsAreReturnedWithoutTextAndOfferedToolsReachTheEngine() {
		this.engine.session.script(result("", FinishReason.TOOL_CALL,
				List.of(new ChatContent.ToolCall("call_1", "getWeather", "{\"city\":\"Munich\"}"))));
		JitLlmChatOptions options = JitLlmChatOptions.builder().toolCallbacks(weatherTool()).build();

		ChatResponse response = chatModel(JitLlmChatOptions.builder().build()).call(new Prompt("Weather?", options));

		AssistantMessage message = response.getResult().getOutput();
		assertThat(message.getText()).isEmpty();
		assertThat(message.getToolCalls()).containsExactly(
				new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Munich\"}"));
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("TOOL_CALLS");
		assertThat(response.hasToolCalls()).isTrue();
		assertThat(this.engine.session.requests.get(0).tools()).singleElement()
			.satisfies(tool -> assertThat(tool.name()).isEqualTo("getWeather"));
	}

	@Test
	void aTruncatedResponseIsReturnedInFullWithLength() {
		this.engine.session.script(result("Once upon a", FinishReason.MAX_TOKENS, List.of()));

		ChatResponse response = chatModel(JitLlmChatOptions.builder().maxTokens(3).build())
			.call(new Prompt("Tell me a story"));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Once upon a");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("LENGTH");
	}

	@Test
	void streamingEmitsVisibleTokensAndAMetadataOnlyFinalChunk() {
		this.engine.session.script(result("<think>hm</think>\n\nHello world", FinishReason.STOP_TOKEN, List.of()),
				"<think>", "hm", "</think>", "\n\n", "Hello", " world");

		List<ChatResponse> chunks = chatModel(JitLlmChatOptions.builder().build()).stream(new Prompt("hi"))
			.collectList()
			.block();

		assertThat(chunks).isNotNull();
		StringBuilder text = new StringBuilder();
		for (ChatResponse chunk : chunks.subList(0, chunks.size() - 1)) {
			text.append(chunk.getResult().getOutput().getText());
		}
		assertThat(text.toString()).isEqualTo("Hello world");
		ChatResponse last = chunks.get(chunks.size() - 1);
		assertThat(last.getResult().getOutput().getText()).isEmpty();
		assertThat(last.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
		assertThat(last.getResult().getOutput().getMetadata()).containsEntry(JitLlmChatModel.THINKING_METADATA_KEY,
				"hm");
	}

	@Test
	void aCancelledStreamStopsTheGeneration() {
		this.engine.session.script(result("one two three four five", FinishReason.STOP_TOKEN, List.of()), "one", " two",
				" three", " four", " five");

		List<ChatResponse> chunks = chatModel(JitLlmChatOptions.builder().build()).stream(new Prompt("count"))
			.take(2)
			.collectList()
			.block(Duration.ofSeconds(10));

		assertThat(chunks).hasSize(2);
		GenerationRequest request = this.engine.session.requests.get(0);
		assertThat(request.cancellation()).isNotNull();
		assertThat(request.cancellation().isCancelled()).isTrue();
		assertThat(this.engine.session.tokensEmitted).isLessThan(5);
	}

	@Test
	void aCallCannotBeCancelledAndCarriesNoToken() {
		this.engine.session.script(result("ok", FinishReason.STOP_TOKEN, List.of()));

		chatModel(JitLlmChatOptions.builder().build()).call(new Prompt("hi"));

		assertThat(this.engine.session.requests.get(0).cancellation()).isNull();
	}

	@Test
	void anErrorDuringStreamingFailsTheStreamInsteadOfHangingIt() {
		this.engine.session.failWith(new NoClassDefFoundError("uk/ac/manchester/tornado/api/types/arrays/IntArray"));

		assertThatIllegalStateException()
			.isThrownBy(() -> chatModel(JitLlmChatOptions.builder().build()).stream(new Prompt("hi"))
				.collectList()
				.block(Duration.ofSeconds(10)))
			.withMessageStartingWith("jitLLM generation failed")
			.withCauseInstanceOf(NoClassDefFoundError.class);
	}

	@Test
	void streamingWithToolsAnswersInOneChunk() {
		this.engine.session.script(result("", FinishReason.TOOL_CALL,
				List.of(new ChatContent.ToolCall("call_1", "getWeather", "{\"city\":\"Munich\"}"))));
		JitLlmChatOptions options = JitLlmChatOptions.builder().toolCallbacks(weatherTool()).build();

		List<ChatResponse> chunks = chatModel(JitLlmChatOptions.builder().build())
			.stream(new Prompt("Weather?", options))
			.collectList()
			.block();

		assertThat(chunks).singleElement()
			.satisfies(chunk -> assertThat(chunk.getResult().getOutput().getToolCalls()).hasSize(1));
	}

	@Test
	void closeClosesTheSessionBeforeTheModelAndRejectsLaterCalls() {
		JitLlmChatModel model = chatModel(JitLlmChatOptions.builder().build());
		this.engine.session.script(result("ok", FinishReason.STOP_TOKEN, List.of()));
		model.call(new Prompt("hi"));

		model.close();
		model.close();

		assertThat(this.engine.events).containsExactly("session.close", "model.close");
		assertThatIllegalStateException().isThrownBy(() -> model.call(new Prompt("again")));
	}

	@Test
	void thinkingFilterHoldsBackASplitTag() {
		JitLlmChatModel.ThinkingFilter filter = new JitLlmChatModel.ThinkingFilter();
		assertThat(filter.accept("Hi <th")).isEqualTo("Hi ");
		assertThat(filter.accept("ink>secret</think> there")).isEqualTo(" there");
		assertThat(filter.accept(" <b>")).isEqualTo(" <b>");
	}

	private static FunctionToolCallback<?, ?> weatherTool() {
		return FunctionToolCallback.builder("getWeather", (Map<String, String> input) -> "sunny")
			.description("Current weather in a city")
			.inputType(Map.class)
			.build();
	}

	private static GenerationResult result(String text, FinishReason reason, List<ChatContent.ToolCall> calls) {
		return new GenerationResult(text, 7, 3, reason, new GenerationTimings(Duration.ZERO, Duration.ZERO, 7, 3),
				calls);
	}

	static final class FakeModel implements TextGenerationModel {

		final List<String> events = new ArrayList<>();

		final FakeSession session = new FakeSession(this.events);

		int sessionsCreated;

		@Override
		public GenerationSession newSession() {
			this.sessionsCreated++;
			return this.session;
		}

		@Override
		public GenerationSession newSession(SessionOptions options) {
			return newSession();
		}

		@Override
		public ModelInfo info() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ModelConfiguration configuration() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void close() {
			this.events.add("model.close");
		}

	}

	static final class FakeSession implements GenerationSession {

		final List<GenerationRequest> requests = new ArrayList<>();

		private final List<String> events;

		int resets;

		int tokensEmitted;

		private GenerationResult next;

		private List<String> tokens = List.of();

		private Error failure;

		FakeSession(List<String> events) {
			this.events = events;
		}

		void failWith(Error failure) {
			this.failure = failure;
		}

		void script(GenerationResult result, String... tokens) {
			this.next = result;
			this.tokens = List.of(tokens);
		}

		@Override
		public GenerationResult generate(GenerationRequest request) {
			this.requests.add(request);
			if (this.failure != null) {
				throw this.failure;
			}
			if (request.onEvent() != null) {
				for (String token : this.tokens) {
					// The engine checks the token between tokens and stops with
					// CANCELLED.
					if (request.cancellation() != null && request.cancellation().isCancelled()) {
						return result(this.next.text(), FinishReason.CANCELLED, List.of());
					}
					request.onEvent().accept(new GenerationEvent(0, token));
					this.tokensEmitted++;
				}
			}
			return this.next;
		}

		@Override
		public int position() {
			return 0;
		}

		@Override
		public void reset() {
			this.resets++;
		}

		@Override
		public void close() {
			this.events.add("session.close");
		}

	}

}
