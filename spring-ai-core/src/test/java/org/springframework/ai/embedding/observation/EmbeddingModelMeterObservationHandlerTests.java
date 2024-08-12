/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.embedding.observation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.observation.conventions.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Unit tests for {@link EmbeddingModelMeterObservationHandler}.
 *
 * @author Thomas Vitale
 */
class EmbeddingModelMeterObservationHandlerTests {

	private MeterRegistry meterRegistry;

	private ObservationRegistry observationRegistry;

	@BeforeEach
	void setUp() {
		this.meterRegistry = new SimpleMeterRegistry();
		this.observationRegistry = ObservationRegistry.create();
		this.observationRegistry.observationConfig()
			.observationHandler(new EmbeddingModelMeterObservationHandler(this.meterRegistry));
	}

	@Test
	void shouldCreateAllMetersDuringAnObservation() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultEmbeddingModelObservationConvention(), () -> observationContext,
					observationRegistry)
			.start();

		observationContext.setResponse(new EmbeddingResponse(List.of(),
				new EmbeddingResponseMetadata("mistral-42", new TestUsage(), Map.of())));

		observation.stop();

		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), AiOperationType.EMBEDDING.value())
			.tag(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider")
			.tag(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "mistral")
			.tag(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), "mistral-42")
			.meters()).hasSize(3);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
			.meters()).hasSize(1);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
			.meters()).hasSize(1);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.meters()).hasSize(1);
	}

	private EmbeddingModelObservationContext generateObservationContext() {
		return EmbeddingModelObservationContext.builder()
			.embeddingRequest(generateEmbeddingRequest())
			.provider("superprovider")
			.requestOptions(EmbeddingOptionsBuilder.builder().withModel("mistral").build())
			.build();
	}

	private EmbeddingRequest generateEmbeddingRequest() {
		return new EmbeddingRequest(List.of(), EmbeddingOptionsBuilder.builder().build());
	}

	static class TestUsage implements Usage {

		@Override
		public Long getPromptTokens() {
			return 1000L;
		}

		@Override
		public Long getGenerationTokens() {
			return 0L;
		}

		@Override
		public Long getTotalTokens() {
			return 1000L;
		}

	}

}
