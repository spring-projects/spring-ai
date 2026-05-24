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

package org.springframework.ai.openai;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Context configuration for OpenAI Java SDK tests.
 *
 * @author Julien Dubois
 * @author Soby Chacko
 */
@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiEmbeddingModel openAiEmbeddingModel() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		return new OpenAiEmbeddingModel(OpenAiEmbeddingOptions.builder()
			.apiKey(apiKey)
			.model(OpenAiEmbeddingOptions.DEFAULT_EMBEDDING_MODEL)
			.build());
	}

	@Bean
	public OpenAiImageModel openAiImageModel() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		return new OpenAiImageModel(
				OpenAiImageOptions.builder().apiKey(apiKey).model(OpenAiImageOptions.DEFAULT_IMAGE_MODEL).build());
	}

	@Bean
	public OpenAiChatModel openAiChatModel() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		return OpenAiChatModel.builder()
			.options(OpenAiChatOptions.builder().apiKey(apiKey).model(OpenAiChatOptions.DEFAULT_CHAT_MODEL).build())
			.build();
	}

	@Bean
	public OpenAiAudioTranscriptionModel openAiSdkAudioTranscriptionModel() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		return OpenAiAudioTranscriptionModel.builder()
			.options(OpenAiAudioTranscriptionOptions.builder().apiKey(apiKey).build())
			.build();
	}

	@Bean
	public OpenAiAudioSpeechModel openAiAudioSpeechModel() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		return OpenAiAudioSpeechModel.builder()
			.defaultOptions(OpenAiAudioSpeechOptions.builder()
				.apiKey(apiKey)
				.model(OpenAiAudioSpeechOptions.DEFAULT_SPEECH_MODEL)
				.build())
			.build();
	}

	@Bean
	public OpenAiModerationModel openAiModerationModel() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		return OpenAiModerationModel.builder()
			.options(OpenAiModerationOptions.builder()
				.apiKey(apiKey)
				.model(OpenAiModerationOptions.DEFAULT_MODERATION_MODEL)
				.build())
			.build();
	}

}
