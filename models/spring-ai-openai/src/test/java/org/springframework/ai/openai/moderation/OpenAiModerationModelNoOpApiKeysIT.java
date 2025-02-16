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

package org.springframework.ai.openai.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author Ilayaperumal Gopinathan
 */
@SpringBootTest(classes = OpenAiModerationModelNoOpApiKeysIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiModerationModelNoOpApiKeysIT {

	@Autowired
	private OpenAiModerationModel moderationModel;

	@Test
	void checkNoOpKey() {
		assertThatThrownBy(() -> {
			ModerationPrompt prompt = new ModerationPrompt("I want to kill them..");

			this.moderationModel.call(prompt);
		}).isInstanceOf(NonTransientAiException.class);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiModerationApi moderationGenerationApi() {
			return OpenAiModerationApi.builder().apiKey(new NoopApiKey()).build();
		}

		@Bean
		public OpenAiModerationModel openAiModerationClient(OpenAiModerationApi openAiModerationApi) {
			return new OpenAiModerationModel(openAiModerationApi);
		}

	}

}
