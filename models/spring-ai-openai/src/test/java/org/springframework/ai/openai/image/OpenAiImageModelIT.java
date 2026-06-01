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

package org.springframework.ai.openai.image;

import com.openai.models.images.ImageModel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiImageModel}.
 *
 * @author Julien Dubois
 */
@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageModelIT {

	private final Logger logger = LoggerFactory.getLogger(OpenAiImageModelIT.class);

	@Autowired
	private OpenAiImageModel imageModel;

	@Test
	void imageAsUrlTest() {
		var options = ImageOptionsBuilder.builder()
			.model(ImageModel.GPT_IMAGE_1_MINI.asString())
			.height(1024)
			.width(1024)
			.build();

		var instructions = """
				A cup of coffee at a restaurant table in Paris, France.
				""";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);

		assertThat(imageResponse.getResults()).hasSize(1);

		ImageResponseMetadata imageResponseMetadata = imageResponse.getMetadata();
		assertThat(imageResponseMetadata.getCreated()).isPositive();

		var generation = imageResponse.getResult();
		Image image = generation.getOutput();
		assertThat(image.getUrl()).isNull();
		assertThat(image.getB64Json()).isNotEmpty();

		var imageGenerationMetadata = generation.getMetadata();
		Assertions.assertThat(imageGenerationMetadata).isInstanceOf(OpenAiImageGenerationMetadata.class);

		OpenAiImageGenerationMetadata openAiSdkImageGenerationMetadata = (OpenAiImageGenerationMetadata) imageGenerationMetadata;

		assertThat(openAiSdkImageGenerationMetadata).isNotNull();

	}

}
