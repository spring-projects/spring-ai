/*
 * Copyright 2025-2025 the original author or authors.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author Ilayaperumal Gopinathan
 */
@SpringBootTest(classes = OpenAiImageModelNoOpApiKeysIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageModelNoOpApiKeysIT {

	@Autowired
	private OpenAiImageModel imageModel;

	@Test
	void checkNoOpKey() {
		assertThatThrownBy(() -> {
			var options = ImageOptionsBuilder.builder().height(1024).width(1024).build();

			var instructions = """
					A light cream colored mini golden doodle with a sign that contains the message "I'm on my way to BARCADE!".""";

			ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

			this.imageModel.call(imagePrompt);
		}).isInstanceOf(NonTransientAiException.class);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiImageApi openAiImageApi() {
			return OpenAiImageApi.builder().apiKey(new NoopApiKey()).build();
		}

		@Bean
		public OpenAiImageModel openAiImageModel(OpenAiImageApi openAiImageApi) {
			return new OpenAiImageModel(openAiImageApi, OpenAiImageOptions.builder().build(),
					new RetryTemplate(RetryPolicy.withDefaults()), TestObservationRegistry.create());
		}

	}

}
