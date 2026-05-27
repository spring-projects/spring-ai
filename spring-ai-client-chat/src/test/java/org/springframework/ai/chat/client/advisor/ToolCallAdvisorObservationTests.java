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

package org.springframework.ai.chat.client.advisor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClientBuilder;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests that reproduce the observation bugs reported in
 * https://github.com/spring-projects/spring-ai/issues/5167.
 *
 * <p>
 * In streaming mode with tool calling, the {@link DefaultAroundAdvisorChain} wraps the
 * entire multi-round stream returned by {@link ToolCallAdvisor#adviseStream} with a
 * single outer {@link org.springframework.ai.chat.client.ChatClientMessageAggregator}.
 * This causes two bugs in the recorded {@link AdvisorObservationContext}:
 *
 * <ul>
 * <li><b>Bug 1</b>: {@code toolCalls} is {@code null} — tool-call chunks are filtered out
 * by {@link ToolCallAdvisor} before the outer aggregator sees them.</li>
 * <li><b>Bug 2</b>: {@code textContent} is cumulative — text from both LLM rounds is
 * concatenated into the single outer observation.</li>
 * </ul>
 *
 * <p>
 * Each test asserts the <em>correct</em> behavior. They currently <strong>fail</strong>
 * with the buggy code and will pass once the fix is applied.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class ToolCallAdvisorObservationTests {

	static final String TOOL_NAME = "getCurrentWeather";

	static final String ROUND1_TEXT = "Let me check the weather for you.";

	static final String ROUND2_TEXT = "The weather in Tokyo is 10 degrees Celsius.";

	@Mock
	ChatModel chatModel;

	// -------------------------------------------------------------------------
	// Bug 1: toolCalls should not be null in the round-1 observation
	// -------------------------------------------------------------------------

	@Test
	void roundOneObservationShouldRecordToolCalls() {
		setupMockChatModel();

		ObservationCapture capture = new ObservationCapture(1);
		runStreamingRequest(buildChatClientWithObservation(capture));
		capture.await();

		AdvisorObservationContext firstRound = capture.contexts.get(0);
		assertThat(firstRound.getChatClientResponse()).as("Observation context must have a ChatClientResponse")
			.isNotNull();

		assertThat(firstRound.getChatClientResponse().chatResponse().hasToolCalls())
			.as("Bug 1: round-1 observation must record toolCalls — currently null because "
					+ "DefaultAroundAdvisorChain's outer aggregator never sees the filtered tool-call chunks")
			.isTrue();
	}

	// -------------------------------------------------------------------------
	// Bug 2: textContent must not be cumulative across rounds
	// -------------------------------------------------------------------------

	@Test
	void roundOneObservationShouldNotContainRoundTwoText() {
		setupMockChatModel();

		ObservationCapture capture = new ObservationCapture(1);
		runStreamingRequest(buildChatClientWithObservation(capture));
		capture.await();

		AdvisorObservationContext firstRound = capture.contexts.get(0);
		assertThat(firstRound.getChatClientResponse()).isNotNull();

		String round1RecordedText = firstRound.getChatClientResponse().chatResponse().getResult().getOutput().getText();

		assertThat(round1RecordedText)
			.as("Bug 2: round-1 observation text must not include round-2 text — currently the outer "
					+ "DefaultAroundAdvisorChain aggregator concatenates text from both LLM calls")
			.doesNotContain(ROUND2_TEXT);
	}

	// -------------------------------------------------------------------------
	// Combined: each round should produce an independent observation
	// -------------------------------------------------------------------------

	@Test
	void eachRoundShouldProduceIndependentObservation() {
		setupMockChatModel();

		// Wait for 1 observation, then give a brief window for any concurrent second
		// observation before asserting. With buggy code only 1 arrives; with the fix 2
		// do.
		ObservationCapture capture = new ObservationCapture(1);
		String finalContent = runStreamingRequest(buildChatClientWithObservation(capture));
		capture.await();

		assertThat(finalContent).as("Final streamed content must contain the round-2 answer").contains("10");

		try {
			// Brief grace period: give the pipeline time to deliver the second
			// observation if it is coming. With buggy code it never arrives; with the fix
			// it does.
			Thread.sleep(200);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

		// With the bug: only 1 observation is captured (the single outer aggregation
		// spanning the entire multi-round stream). After the fix: 2 observations — one
		// per LLM call.
		assertThat(capture.contexts)
			.as("Bug: only one outer observation is produced, spanning both rounds; "
					+ "expected one independent observation per LLM round")
			.hasSize(2);

		AdvisorObservationContext round1Ctx = capture.contexts.get(0);
		AdvisorObservationContext round2Ctx = capture.contexts.get(1);

		// Round 1: must have tool calls, text from round 1 only
		assertThat(round1Ctx.getChatClientResponse().chatResponse().hasToolCalls())
			.as("Round-1 observation must record tool calls")
			.isTrue();
		assertThat(round1Ctx.getChatClientResponse().chatResponse().getResult().getOutput().getText())
			.as("Round-1 text must not include round-2 text")
			.doesNotContain(ROUND2_TEXT);

		// Round 2: must NOT have tool calls, text from round 2 only
		assertThat(round2Ctx.getChatClientResponse().chatResponse().hasToolCalls())
			.as("Round-2 observation must not have tool calls")
			.isFalse();
		assertThat(round2Ctx.getChatClientResponse().chatResponse().getResult().getOutput().getText())
			.as("Round-2 text must contain the final answer")
			.contains(ROUND2_TEXT);
		assertThat(round2Ctx.getChatClientResponse().chatResponse().getResult().getOutput().getText())
			.as("Round-2 text must not include round-1 text")
			.doesNotContain(ROUND1_TEXT);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void setupMockChatModel() {
		// Round 1: model responds with a preamble text chunk, then requests a tool call.
		ChatResponse round1TextChunk = new ChatResponse(List.of(new Generation(new AssistantMessage(ROUND1_TEXT))));
		ChatResponse round1ToolCallChunk = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call_001", "function", TOOL_NAME,
					"{\"location\":\"Tokyo\",\"lat\":35.68,\"lon\":139.69,\"unit\":\"C\"}")))
			.build())));

		// Round 2: model provides the final answer after tool execution.
		ChatResponse round2TextChunk = new ChatResponse(List.of(new Generation(new AssistantMessage(ROUND2_TEXT))));

		when(this.chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		when(this.chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
			.thenReturn(Flux.just(round1TextChunk, round1ToolCallChunk))
			.thenReturn(Flux.just(round2TextChunk));
	}

	private ChatClient buildChatClientWithObservation(ObservationCapture capture) {
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public void onStop(Observation.Context context) {
				if (context instanceof AdvisorObservationContext ctx
						&& "Tool Calling Advisor".equals(ctx.getAdvisorName())) {
					capture.add(ctx);
				}
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}
		});

		FunctionToolCallback<WeatherRequest, WeatherResponse> weatherTool = FunctionToolCallback
			.builder(TOOL_NAME, new WeatherService())
			.description("Get the current weather in a given location")
			.inputType(WeatherRequest.class)
			.build();

		return new DefaultChatClientBuilder(this.chatModel, registry, null, null, null)
			.defaultAdvisors(ToolCallAdvisor.builder().build())
			// .defaultAdvisors(ToolCallAdvisor.builder().streamToolCallResponses(true).build())
			.defaultTools(tool -> tool.callbacks(weatherTool))
			.build();
	}

	private String runStreamingRequest(ChatClient chatClient) {
		return chatClient.prompt()
			.user("What is the weather in Tokyo?")
			.stream()
			.content()
			.collectList()
			.block()
			.stream()
			.collect(Collectors.joining());
	}

	// -------------------------------------------------------------------------
	// Observation capture helper — provides synchronization so assertions
	// do not race against doFinally firing on the boundedElastic thread.
	// -------------------------------------------------------------------------

	static final class ObservationCapture {

		final List<AdvisorObservationContext> contexts = new CopyOnWriteArrayList<>();

		private final CountDownLatch latch;

		ObservationCapture(int awaitCount) {
			this.latch = new CountDownLatch(awaitCount);
		}

		void add(AdvisorObservationContext ctx) {
			this.contexts.add(ctx);
			this.latch.countDown();
		}

		/** Blocks until {@code awaitCount} observations have been captured (or 5s). */
		void await() {
			try {
				if (!this.latch.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("Timed out waiting for ToolCallAdvisor observations (still missing "
							+ this.latch.getCount() + ")");
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for observations", ex);
			}
		}

	}

	// -------------------------------------------------------------------------
	// Minimal weather tool used in tests
	// -------------------------------------------------------------------------

	record WeatherRequest(@JsonProperty("location") String location, @JsonProperty("lat") double lat,
			@JsonProperty("lon") double lon, @JsonProperty("unit") String unit) {
	}

	record WeatherResponse(double temp, String unit) {
	}

	static class WeatherService implements java.util.function.Function<WeatherRequest, WeatherResponse> {

		@Override
		public WeatherResponse apply(WeatherRequest request) {
			return new WeatherResponse(10, "C");
		}

	}

}
