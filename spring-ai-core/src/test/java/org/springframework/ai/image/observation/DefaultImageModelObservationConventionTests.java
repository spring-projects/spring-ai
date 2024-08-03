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
package org.springframework.ai.image.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultImageModelObservationConvention}.
 *
 * @author Thomas Vitale
 */
class DefaultImageModelObservationConventionTests {

	private final DefaultImageModelObservationConvention observationConvention = new DefaultImageModelObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName()).isEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME);
	}

	@Test
	void shouldHaveContextualName() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.operationMetadata(generateOperationMetadata())
			.requestOptions(ImageModelRequestOptions.builder().model("mistral").build())
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext)).isEqualTo("image mistral");
	}

	@Test
	void supportsOnlyImageModelObservationContext() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.operationMetadata(generateOperationMetadata())
			.requestOptions(ImageModelRequestOptions.builder().model("mistral").build())
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveRequiredLowCardinalityKeyValues() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.operationMetadata(generateOperationMetadata())
			.requestOptions(ImageModelRequestOptions.builder().model("mistral").build())
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(AiObservationAttributes.AI_OPERATION_TYPE.value(), "image"),
				KeyValue.of(AiObservationAttributes.AI_PROVIDER.value(), "ollama"),
				KeyValue.of(AiObservationAttributes.REQUEST_MODEL.value(), "mistral"));
	}

	@Test
	void shouldHaveOptionalHighCardinalityKeyValues() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.operationMetadata(generateOperationMetadata())
			.requestOptions(ImageModelRequestOptions.builder()
				.model("mistral")
				.n(1)
				.height(1080)
				.width(1920)
				.style("sketch")
				.responseFormat("base64")
				.build())
			.build();

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_RESPONSE_FORMAT.value(), "base64"),
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_SIZE.value(), "1920x1080"),
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_STYLE.value(), "sketch"));
	}

	@Test
	void shouldHaveMissingHighCardinalityKeyValues() {
		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(generateImagePrompt())
			.operationMetadata(generateOperationMetadata())
			.requestOptions(ImageModelRequestOptions.builder().model("mistral").build())
			.build();

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_RESPONSE_FORMAT.value(), KeyValue.NONE_VALUE),
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_SIZE.value(), KeyValue.NONE_VALUE),
				KeyValue.of(AiObservationAttributes.REQUEST_IMAGE_STYLE.value(), KeyValue.NONE_VALUE));
	}

	private ImagePrompt generateImagePrompt() {
		return new ImagePrompt("here comes the sun");
	}

	private AiOperationMetadata generateOperationMetadata() {
		return AiOperationMetadata.builder()
			.operationType(AiOperationType.IMAGE.value())
			.provider(AiProvider.OLLAMA.value())
			.build();
	}

}