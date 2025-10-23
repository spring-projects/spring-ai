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

package org.springframework.ai.openaiofficial.image;

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
import org.springframework.ai.openaiofficial.OpenAiOfficialImageModel;
import org.springframework.ai.openaiofficial.OpenAiOfficialTestConfiguration;
import org.springframework.ai.openaiofficial.metadata.OpenAiOfficialImageGenerationMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observation instrumentation in {@link OpenAiOfficialImageModel}.
 *
 * @author Julien Dubois
 */
@SpringBootTest(classes = OpenAiOfficialTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiOfficialImageModelIT {

	private final Logger logger = LoggerFactory.getLogger(OpenAiOfficialImageModelIT.class);

	@Autowired
	private OpenAiOfficialImageModel imageModel;

	@Test
	void imageAsUrlTest() {
		var options = ImageOptionsBuilder.builder().height(1024).width(1024).build();

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
		assertThat(image.getUrl()).isNotEmpty();
		logger.info("Generated image URL: {}", image.getUrl());
		assertThat(image.getB64Json()).isNull();

		var imageGenerationMetadata = generation.getMetadata();
		Assertions.assertThat(imageGenerationMetadata).isInstanceOf(OpenAiOfficialImageGenerationMetadata.class);

		OpenAiOfficialImageGenerationMetadata openAiOfficialImageGenerationMetadata = (OpenAiOfficialImageGenerationMetadata) imageGenerationMetadata;

		assertThat(openAiOfficialImageGenerationMetadata).isNotNull();
		assertThat(openAiOfficialImageGenerationMetadata.getRevisedPrompt()).isNotBlank();

	}

}
