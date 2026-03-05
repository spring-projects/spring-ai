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

package org.springframework.ai.chat.observation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiTokenType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Unit tests for {@link ChatModelMeterObservationHandler}.
 *
 * @author Thomas Vitale
 * @author Alexandros Pappas
 */
class ChatModelMeterObservationHandlerTests {

	private MeterRegistry meterRegistry;

	private ObservationRegistry observationRegistry;

	@BeforeEach
	void setUp() {
		this.meterRegistry = new SimpleMeterRegistry();
		this.observationRegistry = ObservationRegistry.create();
		this.observationRegistry.observationConfig()
			.observationHandler(new ChatModelMeterObservationHandler(this.meterRegistry));
	}

	@Test
	void shouldCreateAllMetersDuringAnObservation() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultChatModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observationContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("test"))),
				ChatResponseMetadata.builder().model("mistral-42").usage(new TestUsage()).build()));

		observation.stop();

		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), AiOperationType.CHAT.value())
			.tag(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider")
			.tag(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "mistral")
			.tag(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), "mistral-42")
			.meters()).hasSize(3);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
			.meters()).hasSize(1);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
			.meters()).hasSize(1);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.meters()).hasSize(1);
	}

	@Test
	void shouldHandleNullUsageGracefully() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultChatModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observationContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("test"))),
				ChatResponseMetadata.builder().model("model").usage(null).build()));

		observation.stop();

		assertThat(this.meterRegistry.getMeters())
			.noneMatch(meter -> meter.getId().getName().equals(AiObservationMetricNames.TOKEN_USAGE.value()));
	}

	@Test
	void shouldHandleEmptyGenerations() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultChatModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observationContext.setResponse(new ChatResponse(List.of(),
				ChatResponseMetadata.builder().model("model").usage(new TestUsage()).build()));

		observation.stop();

		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
	}

	@Test
	void shouldHandleMultipleGenerations() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultChatModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		var generations = List.of(new Generation(new AssistantMessage("response1")),
				new Generation(new AssistantMessage("response2")), new Generation(new AssistantMessage("response3")));

		observationContext.setResponse(new ChatResponse(generations,
				ChatResponseMetadata.builder().model("model").usage(new TestUsage()).build()));

		observation.stop();

		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
	}

	@Test
	void shouldHandleObservationWithoutResponse() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultChatModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observation.stop();

		assertThat(this.meterRegistry.getMeters())
			.noneMatch(meter -> meter.getId().getName().equals(AiObservationMetricNames.TOKEN_USAGE.value()));
	}

	private ChatModelObservationContext generateObservationContext() {
		return ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
	}

	private Prompt generatePrompt(ChatOptions chatOptions) {
		return new Prompt("hello", chatOptions);
	}

	static class TestUsage implements Usage {

		@Override
		public Integer getPromptTokens() {
			return 1000;
		}

		@Override
		public Integer getCompletionTokens() {
			return 500;
		}

		@Override
		public Map<String, Integer> getNativeUsage() {
			Map<String, Integer> usage = new HashMap<>();
			usage.put("promptTokens", getPromptTokens());
			usage.put("completionTokens", getCompletionTokens());
			usage.put("totalTokens", getTotalTokens());
			return usage;
		}

	}

}
