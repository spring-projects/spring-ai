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

package org.springframework.ai.chat.client.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the order of observation start/stop events during streaming with tool calls.
 *
 * @author yaner-here
 */
class ChatClientStreamingObservationOrderTests {

	private static final ChatModelObservationConvention DEFAULT_MODEL_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final String CHAT_CLIENT_OBSERVATION = "spring.ai.chat.client";

	private static final String ADVISOR_OBSERVATION = "spring.ai.advisor";

	private static final String MODEL_OBSERVATION = "gen_ai.client.operation";

	private static final String TOOL_OBSERVATION = "spring.ai.tool";

	@Test
	void streamingObservationsShouldFollowCorrectOrder() {
		ObservationRegistry observationRegistry = ObservationRegistry.create();
		EventRecorder handler = new EventRecorder();
		observationRegistry.observationConfig().observationHandler(handler);

		// Build the ChatClient with a model that creates observations and returns tool
		// call responses
		ChatModel model = new ObservingChatModel(observationRegistry);
		ChatClient chatClient = ChatClient.builder(model, observationRegistry, null, null).build();
		ToolCallback toolCallback = DefaultChatClientObservationConventionTests.dummyFunction("testTool");
		chatClient.prompt().user("test message").toolCallbacks(toolCallback).stream().content().blockLast();
		List<String> events = handler.getStartStopSequence();
		System.out.println("Observation events: " + events);

		// Verify the start/stop order matches the expected sequence.
		assertThat(events).containsExactly("start:" + CHAT_CLIENT_OBSERVATION, "start:" + ADVISOR_OBSERVATION,
				"start:" + MODEL_OBSERVATION, "stop:" + MODEL_OBSERVATION, "start:" + TOOL_OBSERVATION,
				"stop:" + TOOL_OBSERVATION, "start:" + MODEL_OBSERVATION, "stop:" + MODEL_OBSERVATION,
				"stop:" + ADVISOR_OBSERVATION, "stop:" + CHAT_CLIENT_OBSERVATION);

		// Verify each observation has the correct parent
		assertThat(handler.getParentObservation(CHAT_CLIENT_OBSERVATION)).as("chat.client parent").isNull();
		assertThat(handler.getParentObservation(ADVISOR_OBSERVATION)).as("advisor parent")
			.isEqualTo(CHAT_CLIENT_OBSERVATION);
		assertThat(handler.getParentObservation(TOOL_OBSERVATION)).as("tool parent").isEqualTo(ADVISOR_OBSERVATION);
	}

	/**
	 * Records observation start/stop events and parent relationships.
	 */
	static class EventRecorder implements ObservationHandler<Observation.Context> {

		final List<String> events = new ArrayList<>();

		final List<String> parentRelationships = new ArrayList<>();

		@Override
		public void onStart(Observation.Context context) {
			String name = context.getName();
			this.events.add("start:" + name);
			String parentName = (context.getParentObservation() != null)
					? context.getParentObservation().getContextView().getName() : null;
			this.parentRelationships.add(name + " -> " + parentName);
		}

		@Override
		public void onStop(Observation.Context context) {
			this.events.add("stop:" + context.getName());
		}

		@Override
		public boolean supportsContext(Observation.Context context) {
			return true;
		}

		List<String> getStartStopSequence() {
			return this.events;
		}

		String getParentObservation(String observationName) {
			return this.parentRelationships.stream()
				.filter(r -> r.startsWith(observationName + " -> "))
				.map(r -> r.substring((observationName + " -> ").length()))
				.findFirst()
				.map(name -> "null".equals(name) ? null : name)
				.orElse(null);
		}

	}

	static class ObservingChatModel implements ChatModel {

		private final ObservationRegistry observationRegistry;

		private final AtomicInteger invocationCount = new AtomicInteger(0);

		ObservingChatModel(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
		}

		@Override
		public ChatOptions getDefaultOptions() {
			return ToolCallingChatOptions.builder().build();
		}

		@Override
		public @NonNull ChatResponse call(@NonNull Prompt prompt) {
			return getStreamResponse(false);
		}

		@Override
		public @NonNull Flux<ChatResponse> stream(@NonNull Prompt prompt) {
			return Flux.deferContextual(contextView -> {
				int callIndex = this.invocationCount.getAndIncrement();

				// Determine if this is the first call (with tool call) or subsequent
				// (final answer)
				boolean firstCall = (callIndex == 0);

				ChatModelObservationContext modelObsContext = ChatModelObservationContext.builder()
					.prompt(prompt)
					.provider("test")
					.build();
				Observation modelObservation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(null,
						DEFAULT_MODEL_OBSERVATION_CONVENTION, () -> modelObsContext, this.observationRegistry);
				modelObservation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
					.start();
				ChatResponse response = getStreamResponse(firstCall);
				Flux<ChatResponse> result;
				if (firstCall) {
					// First call: emit tool call response, execute tool, then recurse
					result = Flux.just(response)
						.doFinally(signalType -> modelObservation.stop())
						.concatWith(Flux.defer(() -> executeToolAndRecurse(contextView, prompt)));
				}
				else {
					// Subsequent call: emit final response and stop
					result = Flux.just(response).doFinally(signalType -> modelObservation.stop());
				}

				return result.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, modelObservation));
			});
		}

		private Flux<ChatResponse> executeToolAndRecurse(ContextView contextView, Prompt prompt) {
			ToolCallingObservationContext toolObsContext = ToolCallingObservationContext.builder()
				.toolDefinition(DefaultToolDefinition.builder().name("testTool").inputSchema("{}").build())
				.toolCallId("call-1")
				.build();

			Observation toolObservation = ToolCallingObservationDocumentation.TOOL_CALL.observation(null,
					new DefaultToolCallingObservationConvention(), () -> toolObsContext, this.observationRegistry);

			Observation parentObs = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			toolObservation.parentObservation(parentObs).start();

			toolObsContext.setToolCallResult("tool result");
			toolObservation.stop();

			// Recursive model call
			ChatModelObservationContext recursiveModelObsContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider("test")
				.build();
			Observation recursiveModelObservation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					null, DEFAULT_MODEL_OBSERVATION_CONVENTION, () -> recursiveModelObsContext,
					this.observationRegistry);
			recursiveModelObservation.parentObservation(toolObservation).start();
			ChatResponse finalResponse = getStreamResponse(false);
			return Flux.just(finalResponse).doFinally(signalType -> recursiveModelObservation.stop());
		}

		private static ChatResponse getStreamResponse(boolean withToolCall) {
			Generation generation;
			if (withToolCall) {
				AssistantMessage assistantMessage = AssistantMessage.builder()
					.content("Let me check that")
					.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "testTool", "{}")))
					.build();
				generation = new Generation(assistantMessage);
			}
			else {
				generation = new Generation(new AssistantMessage("Here is the final answer."));
			}
			return ChatResponse.builder().generations(List.of(generation)).build();
		}

	}

}
