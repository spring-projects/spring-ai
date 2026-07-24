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

package org.springframework.ai.google.genai.image;

import com.google.genai.Client;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for image models {@link GoogleGenAiImageModel}.
 *
 * @author Olivier Le Quellec
 */
@SpringBootTest(classes = GoogleGenAiImageModelIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleGenAiImageModelIT {

	@Autowired
	private GoogleGenAiImageModel imageModel;

	@Autowired
	private Client genAiClient;

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gemini-2.5-flash-image" })
	void defaultImage(String modelName) {
		assertThat(this.imageModel).isNotNull();

		var options = GoogleGenAiImageOptions.builder().model(modelName).n(1).build();

		ImagePrompt imagePrompt = new ImagePrompt("A light cream colored mini golden doodle dog", options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);

		assertThat(imageResponse.getResults()).hasSize(1);

		ImageGeneration imageGeneration = imageResponse.getResults().get(0);
		assertThat(imageGeneration.getOutput()).isNotNull();
		assertThat(imageGeneration.getOutput().getB64Json()).isNotBlank();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public GoogleGenAiImageConnectionDetails connectionDetails() {
			return GoogleGenAiImageConnectionDetails.builder().apiKey(System.getenv("GOOGLE_API_KEY")).build();
		}

		@Bean
		public Client genAiClient(GoogleGenAiImageConnectionDetails connectionDetails) {
			return connectionDetails.getGenAiClient();
		}

		@Bean
		public GoogleGenAiImageModel googleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails) {

			GoogleGenAiImageOptions options = GoogleGenAiImageOptions.builder()
				.model(GoogleGenAiImageModelName.GEMINI_2_5_FLASH_IMAGE.getName())
				.build();

			return new GoogleGenAiImageModel(connectionDetails, options);
		}

	}

}
