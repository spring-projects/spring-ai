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

package org.springframework.ai.openaisdk.image;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openaisdk.OpenAiSdkImageModel;
import org.springframework.ai.openaisdk.OpenAiSdkImageOptions;
import org.springframework.ai.openaisdk.OpenAiSdkTestConfigurationWithObservability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.openai.models.images.ImageModel.DALL_E_3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.image.observation.ImageModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link OpenAiSdkImageModel}.
 *
 * @author Julien Dubois
 */
@SpringBootTest(classes = OpenAiSdkTestConfigurationWithObservability.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiSdkImageModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	private OpenAiSdkImageModel imageModel;

	@BeforeEach
	void setUp() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForImageOperation() throws InterruptedException {
		var options = OpenAiSdkImageOptions.builder()
			.model(DALL_E_3.asString())
			.height(1024)
			.width(1024)
			.responseFormat("url")
			.style("natural")
			.build();

		var instructions = """
				A cup of coffee at a restaurant table in Paris, France.
				""";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);
		assertThat(imageResponse.getResults()).hasSize(1);

		Thread.sleep(200); // Wait for observation to be recorded

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultImageModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("image " + DALL_E_3.asString())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.IMAGE.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.OPENAI_SDK.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), DALL_E_3.asString())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_IMAGE_SIZE.asString(), "1024x1024")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_IMAGE_RESPONSE_FORMAT.asString(), "url")
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
