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
		return new OpenAiEmbeddingModel();
	}

	@Bean
	public OpenAiImageModel openAiImageModel() {
		return new OpenAiImageModel();
	}

	@Bean
	public OpenAiChatModel openAiChatModel() {
		return OpenAiChatModel.builder().build();
	}

	@Bean
	public OpenAiAudioTranscriptionModel openAiSdkAudioTranscriptionModel() {
		return OpenAiAudioTranscriptionModel.builder().build();
	}

	@Bean
	public OpenAiAudioSpeechModel openAiAudioSpeechModel() {
		return OpenAiAudioSpeechModel.builder().build();
	}

	@Bean
	public OpenAiModerationModel openAiModerationModel() {
		return OpenAiModerationModel.builder().build();
	}

}
