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

package org.springframework.ai.zhipuai.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.zhipuai.ZhiPuAiImageOptions;
import org.springframework.ai.zhipuai.ZhiPuAiTestConfiguration;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ZhiPuAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
public class ZhiPuAiImageModelIT {

	@Autowired
	protected ImageModel imageModel;

	@Test
	void imageAsUrlTest() {
		var options = ZhiPuAiImageOptions.builder()
			.quality(ZhiPuAiImageApi.Quality.STANDARD)
			.watermarkEnabled(true)
			.size("720x1440")
			.model(ZhiPuAiImageApi.ImageModel.CogView_3_Flash.getValue())
			.build();

		var instructions = """
				A light cream colored mini golden doodle with a sign that contains the message "I'm on my way to BARCADE!".""";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);

		assertThat(imageResponse.getResults()).hasSize(1);

		ImageResponseMetadata imageResponseMetadata = imageResponse.getMetadata();
		assertThat(imageResponseMetadata.getCreated()).isPositive();

		var generation = imageResponse.getResult();
		Image image = generation.getOutput();
		System.out.println(image);
		assertThat(image.getUrl()).isNotEmpty();
		assertThat(image.getB64Json()).isNull();
	}

}
