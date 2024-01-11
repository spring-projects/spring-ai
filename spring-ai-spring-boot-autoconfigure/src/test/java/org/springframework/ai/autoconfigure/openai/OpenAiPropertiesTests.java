/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit Tests for {@link OpenAiProperties}.
 *
 * @author John Blum
 * @since 0.7.1
 */
@SpringBootTest(properties = { "spring.ai.openai.api-key=abc123", "spring.ai.openai.model=claudia-shiffer-5",
		"spring.ai.openai.base-url=https://api.openai.spring.io/eieioh", "spring.ai.openai.temperature=0.5",
		"spring.ai.openai.duration=30s", "spring.ai.openai.embedding.base-url=https://api.openai.spring.io/embedding" })
@SuppressWarnings("unused")
class OpenAiPropertiesTests {

	private static final String EMBEDDING_API_KEY = "test-embedding-api-key";
	private static final String EMBEDDING_BASE_URL = "https://api.openai.spring.io/embedding";
	private static final String OPENAI_API_KEY = "abc123";
	private static final String OPENAI_BASE_URL = "https://api.openai.spring.io/eieioh";

	@Autowired
	private OpenAiProperties openAiProperties;

	@Test
	void openAiPropertiesAreCorrect() {

		assertThat(this.openAiProperties).isNotNull();
		assertThat(this.openAiProperties.getApiKey()).isEqualTo(OPENAI_API_KEY);
		assertThat(this.openAiProperties.getModel()).isEqualTo("claudia-shiffer-5");
		assertThat(this.openAiProperties.getBaseUrl()).isEqualTo(OPENAI_BASE_URL);
		assertThat(this.openAiProperties.getTemperature()).isEqualTo(0.5d);

		OpenAiProperties.Embedding embedding = this.openAiProperties.getEmbedding();

		assertThat(embedding).isNotNull();
		assertThat(embedding.getApiKey()).isEqualTo(this.openAiProperties.getApiKey());
		assertThat(embedding.getModel()).isEqualTo("text-embedding-ada-002");
		assertThat(embedding.getBaseUrl()).isEqualTo(EMBEDDING_BASE_URL);
	}

	@Test
	void embeddingApiKeyAndBaseURLAreCorrect_whenNotSetOpenAiProperties() {
		OpenAiProperties openAiProperties = new OpenAiProperties();
		OpenAiProperties.Embedding embedding = openAiProperties.getEmbedding();

		assertThat(embedding.getApiKey()).isEqualTo(openAiProperties.getApiKey());
		assertThat(embedding.getBaseUrl()).isEqualTo(openAiProperties.getBaseUrl());
	}

	@Test
	void embeddingApiKeyAndBaseURLAreCorrect_whenSetOpenAiProperties() {
		OpenAiProperties openAiProperties = new OpenAiProperties();
		openAiProperties.setApiKey(OPENAI_API_KEY);
		openAiProperties.setBaseUrl(OPENAI_BASE_URL);
		OpenAiProperties.Embedding embedding = openAiProperties.getEmbedding();

		assertThat(embedding.getApiKey()).isEqualTo(openAiProperties.getApiKey());
		assertThat(embedding.getBaseUrl()).isEqualTo(openAiProperties.getBaseUrl());
	}

	@Test
	void embeddingApiKeyAndBaseURLAreCorrect_whenEmbeddingPropertiesAndAfterSetOpenAiProperties() {
		OpenAiProperties openAiProperties = new OpenAiProperties();

		OpenAiProperties.Embedding embedding = openAiProperties.getEmbedding();
		embedding.setApiKey(EMBEDDING_API_KEY);
		embedding.setBaseUrl(EMBEDDING_BASE_URL);

		openAiProperties.setApiKey(OPENAI_API_KEY);
		openAiProperties.setBaseUrl(OPENAI_BASE_URL);

		assertThat(embedding.getApiKey()).isEqualTo(EMBEDDING_API_KEY);
		assertThat(embedding.getBaseUrl()).isEqualTo(EMBEDDING_BASE_URL);
	}

	@SpringBootConfiguration
	@EnableConfigurationProperties(OpenAiProperties.class)
	static class TestConfiguration {

	}

}
