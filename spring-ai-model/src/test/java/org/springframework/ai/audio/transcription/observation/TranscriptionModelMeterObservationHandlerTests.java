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

package org.springframework.ai.audio.transcription.observation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiTokenType;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TranscriptionModelMeterObservationHandler}.
 *
 * @author Olivier Le Quellec
 */
class TranscriptionModelMeterObservationHandlerTests {

	private MeterRegistry meterRegistry;

	private ObservationRegistry observationRegistry;

	@BeforeEach
	void setUp() {
		this.meterRegistry = new SimpleMeterRegistry();
		this.observationRegistry = ObservationRegistry.create();
		this.observationRegistry.observationConfig()
			.observationHandler(new TranscriptionModelMeterObservationHandler(this.meterRegistry));
	}

	@Test
	void shouldCreateAllMetersDuringAnObservation() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultAudioTranscriptionModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
		metadata.setUsage(new TestUsage());
		observationContext.setResponse(new AudioTranscriptionResponse(new AudioTranscription("transcript"), metadata));

		observation.stop();

		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), AiOperationType.TRANSCRIPTION.value())
			.tag(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider")
			.tag(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "chirp_2")
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
	void shouldCreateDurationMeterDuringAnObservation() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultAudioTranscriptionModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		AudioTranscriptionResponseMetadata metadata = new AudioTranscriptionResponseMetadata();
		metadata.setUsage(new TestDurationUsage(12L));
		observationContext.setResponse(new AudioTranscriptionResponse(new AudioTranscription("transcript"), metadata));

		observation.stop();

		assertThat(this.meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).isEmpty();
		assertThat(this.meterRegistry.get(AiObservationMetricNames.OPERATION_DURATION.value())
			.tag(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), AiOperationType.TRANSCRIPTION.value())
			.tag(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider")
			.tag(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "chirp_2")
			.timer()
			.totalTime(TimeUnit.SECONDS)).isEqualTo(12.0);
	}

	@Test
	void shouldNotCreateMetersWhenResponseIsNull() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultAudioTranscriptionModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observation.stop();

		assertThat(this.meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).isEmpty();
		assertThat(this.meterRegistry.find(AiObservationMetricNames.OPERATION_DURATION.value()).meters()).isEmpty();
	}

	private AudioTranscriptionModelObservationContext generateObservationContext() {
		return AudioTranscriptionModelObservationContext.builder()
			.transcriptionPrompt(generateTranscriptionPrompt(new TestOptions("chirp_2")))
			.provider("superprovider")
			.build();
	}

	private AudioTranscriptionPrompt generateTranscriptionPrompt(AudioTranscriptionOptions options) {
		return new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1, 2, 3 }), options);
	}

	private record TestOptions(String model) implements AudioTranscriptionOptions {

		@Override
		public String getModel() {
			return this.model;
		}

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

	static class TestDurationUsage implements Usage {

		private final Long duration;

		TestDurationUsage(Long duration) {
			this.duration = duration;
		}

		@Override
		public Integer getPromptTokens() {
			return 0;
		}

		@Override
		public Integer getCompletionTokens() {
			return 0;
		}

		@Override
		public Integer getTotalTokens() {
			return 0;
		}

		@Override
		public Long getDuration() {
			return this.duration;
		}

		@Override
		public @Nullable Object getNativeUsage() {
			return null;
		}

	}

}
