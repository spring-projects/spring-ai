/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.image;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link OpenAiImageModel}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = OpenAiImageModelObservationIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	OpenAiImageModel imageModel;

	@Test
	void observationForImageOperation() {
		var options = OpenAiImageOptions.builder()
			.model(OpenAiImageApi.ImageModel.DALL_E_3.getValue())
			.height(1024)
			.width(1024)
			.responseFormat("url")
			.style("natural")
			.build();

		var instructions = "Here comes the sun";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);
		assertThat(imageResponse.getResults()).hasSize(1);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image " + OpenAiImageApi.ImageModel.DALL_E_3.getValue())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.OPENAI.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					OpenAiImageApi.ImageModel.DALL_E_3.getValue())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(), "1024x1024")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_IMAGE_RESPONSE_FORMAT.asString(), "url")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public OpenAiImageApi openAiImageApi() {
			return OpenAiImageApi.builder().apiKey(new SimpleApiKey(System.getenv("OPENAI_API_KEY"))).build();
		}

		@Bean
		public OpenAiImageModel openAiImageModel(OpenAiImageApi openAiImageApi,
				TestObservationRegistry observationRegistry) {
			return new OpenAiImageModel(openAiImageApi, OpenAiImageOptions.builder().build(),
					new RetryTemplate(RetryPolicy.withDefaults()), observationRegistry);
		}

	}

}
