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

package org.springframework.ai.togetherai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.togetherai.api.TogetherAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TogetherAiImageTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "TOGETHERAI_API_KEY", matches = ".+")
class TogetherAiImageModelIT {

	@Autowired
	protected ImageModel togetherAiImageModel;

	@Test
	void generateImage_shouldReturnUrlPng() {
		TogetherAiImageOptions imageOptions = TogetherAiImageOptions.builder()
			.model("black-forest-labs/FLUX.1-schnell")
			.outputFormat(TogetherAiImageOptions.OutputFormat.PNG)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.URL)
			.build();
		String instruction = "A green apple on a plate with leaf";
		ImagePrompt imagePrompt = new ImagePrompt(instruction, imageOptions);
		ImageResponse imageResponse = this.togetherAiImageModel.call(imagePrompt);
		ImageGeneration imageGeneration = imageResponse.getResult();
		Image image = imageGeneration.getOutput();
		assertThat(image.getUrl()).isNotEmpty();
		System.out.println(image.getUrl());
	}

}
