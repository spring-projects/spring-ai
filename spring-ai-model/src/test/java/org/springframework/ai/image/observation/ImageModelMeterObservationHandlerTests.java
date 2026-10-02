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

package org.springframework.ai.image.observation;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiTokenType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Unit tests for {@link ImageModelMeterObservationHandler}.
 *
 * @author ZYZ666-RGB
 */
class ImageModelMeterObservationHandlerTests {

	private MeterRegistry meterRegistry;

	private ObservationRegistry observationRegistry;

	@BeforeEach
	void setUp() {
		this.meterRegistry = new SimpleMeterRegistry();
		this.observationRegistry = ObservationRegistry.create();
		this.observationRegistry.observationConfig()
			.observationHandler(new ImageModelMeterObservationHandler(this.meterRegistry));
	}

	@Test
	void shouldCreateUsageMetersDuringAnObservation() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultImageModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observationContext
			.setResponse(new ImageResponse(List.of(), new ImageResponseMetadata(42L, new DefaultUsage(100, 20, 120))));

		observation.stop();

		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(), AiOperationType.IMAGE.value())
			.tag(LowCardinalityKeyNames.AI_PROVIDER.asString(), "superprovider")
			.tag(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "image-model")
			.meters()).hasSize(3);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
			.counter()
			.count()).isEqualTo(100);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
			.counter()
			.count()).isEqualTo(20);
		assertThat(this.meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.counter()
			.count()).isEqualTo(120);
	}

	@Test
	void shouldHandleNullUsageGracefully() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultImageModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observationContext.setResponse(new ImageResponse(List.of(), new ImageResponseMetadata(42L, null)));

		observation.stop();

		assertThat(this.meterRegistry.getMeters())
			.noneMatch(meter -> meter.getId().getName().equals(AiObservationMetricNames.TOKEN_USAGE.value()));
	}

	@Test
	void shouldHandleObservationWithoutResponse() {
		var observationContext = generateObservationContext();
		var observation = Observation
			.createNotStarted(new DefaultImageModelObservationConvention(), () -> observationContext,
					this.observationRegistry)
			.start();

		observation.stop();

		assertThat(this.meterRegistry.getMeters())
			.noneMatch(meter -> meter.getId().getName().equals(AiObservationMetricNames.TOKEN_USAGE.value()));
	}

	private ImageModelObservationContext generateObservationContext() {
		return ImageModelObservationContext.builder()
			.imagePrompt(new ImagePrompt("hello", ImageOptionsBuilder.builder().model("image-model").build()))
			.provider("superprovider")
			.build();
	}

}
