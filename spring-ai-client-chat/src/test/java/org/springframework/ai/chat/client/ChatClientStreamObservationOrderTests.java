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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationView;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for spring-projects/spring-ai#5971: in a streaming {@link ChatClient}
 * call with a tool round, every observation must stop after the observations nested
 * inside it, and the tool observation must be a child of the tool-calling advisor.
 */
class ChatClientStreamObservationOrderTests {

	@Test
	void streamingObservationsStopInnermostFirst() throws Exception {
		ObservationRegistry registry = ObservationRegistry.create();
		List<String> events = new CopyOnWriteArrayList<>();
		List<Boolean> responsesOnStop = new CopyOnWriteArrayList<>();
		CountDownLatch clientStopped = new CountDownLatch(1);
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStart(Observation.Context context) {
				events.add("start " + label(context) + " parent=" + label(context.getParentObservation()));
			}

			@Override
			public void onStop(Observation.Context context) {
				events.add("stop " + label(context));
				// The response must already be on the context when the observation stops,
				// or
				// the handlers that read it on stop (metrics, completion logging) miss
				// it.
				if (context instanceof ChatClientObservationContext clientContext) {
					responsesOnStop.add(clientContext.getResponse() != null);
					clientStopped.countDown();
				}
				if (context instanceof AdvisorObservationContext advisorContext) {
					responsesOnStop.add(advisorContext.getChatClientResponse() != null);
				}
			}
		});

		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
		AtomicInteger calls = new AtomicInteger();
		given(chatModel.stream(any(Prompt.class))).willAnswer(invocation -> {
			if (calls.incrementAndGet() == 1) {
				AssistantMessage toolCall = AssistantMessage.builder()
					.content("")
					.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "weather", "{}")))
					.build();
				return Flux.just(new ChatResponse(List.of(new Generation(toolCall))));
			}
			return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("sunny")))));
		});
		ToolCallback weather = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("weather")
					.description("weather")
					.inputSchema("{\"type\":\"object\"}")
					.build();
			}

			@Override
			public String call(String toolInput) {
				return "sunny";
			}
		};
		ChatClient chatClient = new DefaultChatClientBuilder(chatModel, registry, null, null).build();

		chatClient.prompt("What is the weather in Denver?")
			.toolCallbacks(weather)
			.stream()
			.chatClientResponse()
			.blockLast(Duration.ofSeconds(10));
		// The chat client observation stops last, from the outermost operator, possibly
		// on
		// another thread once the tool round has run on boundedElastic.
		assertThat(clientStopped.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(calls).hasValue(2);

		String client = "chat.client";
		String advisor = "advisor:Tool Calling Advisor";
		String model = "advisor:stream";
		assertThat(events).containsExactly("start " + client + " parent=null", "start " + advisor + " parent=" + client,
				"start " + model + " parent=" + advisor, "stop " + model, "start tool:weather parent=" + advisor,
				"stop tool:weather", "start " + model + " parent=" + advisor, "stop " + model, "stop " + advisor,
				"stop " + client);
		assertThat(responsesOnStop).isNotEmpty().allMatch(Boolean::booleanValue);
	}

	private static String label(ObservationView view) {
		return view == null ? "null" : label(view.getContextView());
	}

	private static String label(Observation.ContextView context) {
		if (context instanceof AdvisorObservationContext advisorContext) {
			return "advisor:" + advisorContext.getAdvisorName();
		}
		if (context instanceof ToolCallingObservationContext toolContext) {
			return "tool:" + toolContext.getToolDefinition().name();
		}
		if (context instanceof ChatClientObservationContext) {
			return "chat.client";
		}
		return String.valueOf(context.getName());
	}

}
