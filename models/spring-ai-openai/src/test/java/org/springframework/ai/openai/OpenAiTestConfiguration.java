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

package org.springframework.ai.openai;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiApi openAiApi() {
		return OpenAiApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public OpenAiImageApi openAiImageApi() {
		return OpenAiImageApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public OpenAiAudioApi openAiAudioApi() {
		return OpenAiAudioApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public OpenAiModerationApi openAiModerationApi() {
		return OpenAiModerationApi.builder().apiKey(getApiKey()).build();
	}

	private ApiKey getApiKey() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
		}
		return new SimpleApiKey(apiKey);
	}

	@Bean
	public OpenAiChatModel openAiChatModel(OpenAiApi api) {
		return OpenAiChatModel.builder()
			.openAiApi(api)
			.defaultOptions(OpenAiChatOptions.builder().model(ChatModel.GPT_4_O_MINI).build())
			.build();
	}

	@Bean
	public OpenAiAudioTranscriptionModel openAiTranscriptionModel(OpenAiAudioApi api) {
		return new OpenAiAudioTranscriptionModel(api);
	}

	@Bean
	public OpenAiAudioSpeechModel openAiAudioSpeechModel(OpenAiAudioApi api) {
		return new OpenAiAudioSpeechModel(api);
	}

	@Bean
	public OpenAiImageModel openAiImageModel(OpenAiImageApi imageApi) {
		return new OpenAiImageModel(imageApi);
	}

	@Bean
	public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi api) {
		return new OpenAiEmbeddingModel(api);
	}

	@Bean
	public OpenAiModerationModel openAiModerationClient(OpenAiModerationApi openAiModerationApi) {
		return new OpenAiModerationModel(openAiModerationApi);
	}

}
