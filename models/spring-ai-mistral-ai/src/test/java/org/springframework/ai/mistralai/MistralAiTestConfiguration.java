/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.mistralai;

import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiModerationApi;
import org.springframework.ai.mistralai.moderation.MistralAiModerationModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Jason Smith
 * @author Nicolas Krier
 */
@SpringBootConfiguration
public class MistralAiTestConfiguration {

	private static String retrieveApiKey() {
		var apiKey = System.getenv("MISTRAL_AI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"Missing MISTRAL_AI_API_KEY environment variable. Please set it to your Mistral AI API key.");
		}
		return apiKey;
	}

	@Bean
	public MistralAiApi mistralAiApi() {
		return MistralAiApi.builder().apiKey(retrieveApiKey()).build();
	}

	@Bean
	public MistralAiModerationApi mistralAiModerationApi() {
		return MistralAiModerationApi.builder().apiKey(retrieveApiKey()).build();
	}

	@Bean
	public MistralAiEmbeddingModel mistralAiEmbeddingModel(MistralAiApi api) {
		return MistralAiEmbeddingModel.builder().mistralAiApi(api).build();
	}

	@Bean
	public MistralAiChatModel mistralAiChatModel(MistralAiApi mistralAiApi) {
		return MistralAiChatModel.builder()
			.mistralAiApi(mistralAiApi)
			.defaultOptions(
					MistralAiChatOptions.builder().model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue()).build())
			.build();
	}

	@Bean
	public MistralAiModerationModel mistralAiModerationModel(MistralAiModerationApi mistralAiModerationApi) {
		return MistralAiModerationModel.builder().mistralAiModerationApi(mistralAiModerationApi).build();
	}

}
