/*
 * Copyright 2024-2024 the original author or authors.
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.*;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageClientIT extends AbstractIT {

	@Test
	void imageAsUrlTest() {
		var options = ImageOptionsBuilder.builder().withHeight(256).withWidth(256).build();

		ImagePrompt imagePrompt = new ImagePrompt("Create an image of a mini golden doodle dog.", options);

		ImageResponse imageResponse = openaiImageClient.call(imagePrompt);

		assertThat(imageResponse.getResults()).hasSize(1);

		ImageResponseMetadata imageResponseMetadata = imageResponse.getMetadata();
		assertThat(imageResponseMetadata.created()).isPositive();

		var generation = imageResponse.getResult();
		Image image = generation.getOutput();
		assertThat(image.getUrl()).isNotEmpty();
		assertThat(image.getB64Json()).isNull();

		var imageGenerationMetadata = generation.getMetadata();
		Assertions.assertThat(imageGenerationMetadata).isInstanceOf(OpenAiImageGenerationMetadata.class);

		OpenAiImageGenerationMetadata openAiImageGenerationMetadata = (OpenAiImageGenerationMetadata) imageGenerationMetadata;

		assertThat(openAiImageGenerationMetadata).isNotNull();
		assertThat(openAiImageGenerationMetadata.getRevisedPrompt()).isNull();
	}

}
