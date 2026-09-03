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

package org.springframework.ai.audio.tts.observation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.tts.TextToSpeechResponseMetadata;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiTokenType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextToSpeechModelMeterObservationHandler}.
 *
 * @author Olivier Le Quellec
 */
class TextToSpeechModelMeterObservationHandlerTests {

	private MeterRegistry meterRegistry;

	private ObservationRegistry observationRegistry;

	@BeforeEach
	void setUp() {
		this.meterRegistry = new SimpleMeterRegistry();
		this.observationRegistry = ObservationRegistry.create();
		this.observationRegistry.observationConfig()
			.observationHandler(new TextToSpeechModelMeterObservationHandler(this.meterRegistry));
	}

	@Test
	void shouldCreateAllMetersDuringAnObservation() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultTextToSpeechModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		TextToSpeechResponseMetadata metadata = new TextToSpeechResponseMetadata();
		metadata.setUsage(new TestUsage());
		observationContext.setResponse(new TextToSpeechResponse(List.of(new Speech(new byte[] { 1 })), metadata));

		observation.stop();

		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), AiOperationType.TEXT_TO_SPEECH.value())
			.tag(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider")
			.tag(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "gemini-2.5-flash-tts")
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
	void shouldNotCreateMetersWhenResponseIsNull() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultTextToSpeechModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observation.stop();

		assertThat(this.meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).isEmpty();
	}

	private TextToSpeechModelObservationContext generateObservationContext() {
		return TextToSpeechModelObservationContext.builder()
			.textToSpeechPrompt(
					generateTextToSpeechPrompt(TextToSpeechOptions.builder().model("gemini-2.5-flash-tts").build()))
			.provider("superprovider")
			.build();
	}

	private TextToSpeechPrompt generateTextToSpeechPrompt(TextToSpeechOptions options) {
		return new TextToSpeechPrompt("Hello world", options);
	}

	static class TestUsage implements Usage {

		@Override
		public Integer getPromptTokens() {
			return 1000;
		}

		@Override
		public Integer getCompletionTokens() {
			return 0;
		}

		@Override
		public Integer getTotalTokens() {
			return 1000;
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
