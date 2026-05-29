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
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolCallObservationThreadLocalTests {

	private TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	@Test
	public void testSyncToolWithMockModel() {
		AtomicReference<Observation> threadLocalObservation = new AtomicReference<>();

		ToolCallback syncTool = FunctionToolCallback.<CityInput, String>builder("getWeather", in -> {
			threadLocalObservation.set(this.observationRegistry.getCurrentObservation());

			// Simulate a synchronous RestTemplate call that creates an observation
			Observation restTemplateObs = Observation.createNotStarted("http.client.requests",
					this.observationRegistry);
			restTemplateObs.start().stop(); // parent will be whatever is in ThreadLocal

			return in.city() + ": 25C";
		}).description("Get weather").inputType(CityInput.class).build();

		ChatModel mockModel = new ChatModel() {
			private boolean first = true;

			@Override
			public ChatResponse call(Prompt prompt) {
				return null;
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.deferContextual(ctx -> {
					// Manually create chat.model observation as models do
					Observation obs = Observation.createNotStarted("chat.model",
							ToolCallObservationThreadLocalTests.this.observationRegistry);
					obs.parentObservation(ctx.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

					ChatResponse response;
					if (this.first) {
						this.first = false;
						AssistantMessage.ToolCall tc = new AssistantMessage.ToolCall("1", "function", "getWeather",
								"{\"city\":\"Paris\"}");
						AssistantMessage msg = org.springframework.ai.chat.messages.AssistantMessage.builder()
							.toolCalls(List.of(tc))
							.build();
						response = new ChatResponse(List.of(new Generation(msg)));
					}
					else {
						response = new ChatResponse(List.of(new Generation(new AssistantMessage("Weather is 25C"))));
					}

					return Flux.just(response)
						.publishOn(Schedulers.parallel()) // Simulate response on
															// different thread!
						.doFinally(st -> obs.stop());
				});
			}

			@Override
			public ChatOptions getDefaultOptions() {
				return org.springframework.ai.model.tool.DefaultToolCallingChatOptions.builder().build();
			}
		};

		ChatClient chatClient = ChatClient.builder(mockModel, this.observationRegistry, null, null, null).build();

		chatClient.prompt("weather in Paris?").tools(t -> t.callbacks(syncTool)).stream().chatResponse().blockLast();

		assertThat(threadLocalObservation.get()).isNotNull();
		assertThat(threadLocalObservation.get().getContextView().getName()).isEqualTo("spring.ai.tool");
	}

	public record CityInput(String city) {
	}

}
