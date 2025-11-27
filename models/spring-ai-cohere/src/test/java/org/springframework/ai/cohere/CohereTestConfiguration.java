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

package org.springframework.ai.cohere;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.ai.cohere.chat.CohereChatOptions;
import org.springframework.ai.cohere.embedding.CohereEmbeddingModel;
import org.springframework.ai.cohere.embedding.CohereMultimodalEmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Ricken Bazolo
 */
@SpringBootConfiguration
public class CohereTestConfiguration {

	private static String retrieveApiKey() {
		var apiKey = System.getenv("COHERE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"Missing COHERE_API_KEY environment variable. Please set it to your Cohere API key.");
		}
		return apiKey;
	}

	@Bean
	public CohereApi cohereApi() {
		return CohereApi.builder().apiKey(retrieveApiKey()).build();
	}

	@Bean
	public CohereChatModel cohereChatModel(CohereApi api) {
		return CohereChatModel.builder()
			.cohereApi(api)
			.defaultOptions(CohereChatOptions.builder().model(CohereApi.ChatModel.COMMAND_A.getValue()).build())
			.build();
	}

	@Bean
	public CohereEmbeddingModel cohereEmbeddingModel(CohereApi api) {
		return CohereEmbeddingModel.builder().cohereApi(api).build();
	}

	@Bean
	public CohereMultimodalEmbeddingModel cohereMultimodalEmbeddingModel(CohereApi api) {
		return CohereMultimodalEmbeddingModel.builder().cohereApi(api).build();
	}

}
