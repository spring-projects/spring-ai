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

package org.springframework.ai.model.stabilityai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.stabilityai.StyleEnum;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "STABILITYAI_API_KEY", matches = ".*")
public class StabilityAiAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.stabilityai.image.api-key=" + System.getenv("STABILITYAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.run(context -> {
			ImageModel imageModel = context.getBean(ImageModel.class);
			StabilityAiImageOptions imageOptions = StabilityAiImageOptions.builder()
				.stylePreset(StyleEnum.PHOTOGRAPHIC)
				.build();

			var instructions = """
					A light cream colored mini golden doodle.
					""";

			ImagePrompt imagePrompt = new ImagePrompt(instructions, imageOptions);
			ImageResponse imageResponse = imageModel.call(imagePrompt);

			ImageGeneration imageGeneration = imageResponse.getResult();
			Image image = imageGeneration.getOutput();

			assertThat(image.getB64Json()).isNotEmpty();
		});
	}

	@Test
	void generateWithCustomTimeout() {
		this.contextRunner
			.withPropertyValues("spring.ai.stabilityai.connect-timeout=1s", "spring.ai.stabilityai.read-timeout=1s")
			.withConfiguration(AutoConfigurations.of(StabilityAiImageAutoConfiguration.class))
			.run(context -> {
				ImageModel imageModel = context.getBean(ImageModel.class);
				StabilityAiImageOptions imageOptions = StabilityAiImageOptions.builder()
					.stylePreset(StyleEnum.PHOTOGRAPHIC)
					.build();

				var instructions = """
						A light cream colored mini golden doodle.
						""";

				ImagePrompt imagePrompt = new ImagePrompt(instructions, imageOptions);
				ImageResponse imageResponse = imageModel.call(imagePrompt);

				ImageGeneration imageGeneration = imageResponse.getResult();
				Image image = imageGeneration.getOutput();

				assertThat(image.getB64Json()).isNotEmpty();
			});
	}

}
