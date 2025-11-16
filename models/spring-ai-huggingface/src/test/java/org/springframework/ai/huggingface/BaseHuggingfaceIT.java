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

package org.springframework.ai.huggingface;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.huggingface.api.common.HuggingfaceApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Base class for Huggingface integration tests. Provides shared configuration and common
 * utilities for testing against the Huggingface API.
 * <p>
 * Integration tests require a valid HuggingFace API key to be set in the
 * HUGGINGFACE_API_KEY environment variable.
 *
 * @author Myeongdeok Kang
 */
@SpringBootTest(classes = BaseHuggingfaceIT.Config.class)
public abstract class BaseHuggingfaceIT {

	protected static final String DEFAULT_CHAT_MODEL = "meta-llama/Llama-3.2-3B-Instruct";

	protected static final String DEFAULT_EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2";

	/**
	 * Get the Huggingface API key from environment variable.
	 * @return the API key
	 * @throws IllegalStateException if the API key is not set
	 */
	protected static String getApiKey() {
		String apiKey = System.getenv("HUGGINGFACE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalStateException(
					"HUGGINGFACE_API_KEY environment variable must be set for integration tests");
		}
		return apiKey;
	}

	/**
	 * Spring Boot configuration for Huggingface integration tests.
	 */
	@SpringBootConfiguration
	static class Config {

		@Bean
		public HuggingfaceApi huggingfaceChatApi() {
			return HuggingfaceApi.builder()
				.baseUrl(HuggingfaceApiConstants.DEFAULT_CHAT_BASE_URL)
				.apiKey(getApiKey())
				.build();
		}

		@Bean
		public HuggingfaceApi huggingfaceEmbeddingApi() {
			return HuggingfaceApi.builder()
				.baseUrl(HuggingfaceApiConstants.DEFAULT_EMBEDDING_BASE_URL)
				.apiKey(getApiKey())
				.build();
		}

		@Bean
		public HuggingfaceChatModel huggingfaceChatModel(HuggingfaceApi huggingfaceChatApi) {
			return HuggingfaceChatModel.builder()
				.huggingfaceApi(huggingfaceChatApi)
				.defaultOptions(HuggingfaceChatOptions.builder().model(DEFAULT_CHAT_MODEL).temperature(0.7).build())
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.observationRegistry(ObservationRegistry.NOOP)
				.build();
		}

		@Bean
		public HuggingfaceEmbeddingModel huggingfaceEmbeddingModel(HuggingfaceApi huggingfaceEmbeddingApi) {
			return HuggingfaceEmbeddingModel.builder()
				.huggingfaceApi(huggingfaceEmbeddingApi)
				.defaultOptions(HuggingfaceEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build())
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.observationRegistry(ObservationRegistry.NOOP)
				.build();
		}

	}

}
