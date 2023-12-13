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

import java.time.Duration;

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

	@Autowired
	private OpenAiProperties openAiProperties;

	@Test
	void openAiPropertiesAreCorrect() {

		assertThat(this.openAiProperties).isNotNull();
		assertThat(this.openAiProperties.getApiKey()).isEqualTo("abc123");
		assertThat(this.openAiProperties.getModel()).isEqualTo("claudia-shiffer-5");
		assertThat(this.openAiProperties.getBaseUrl()).isEqualTo("https://api.openai.spring.io/eieioh");
		assertThat(this.openAiProperties.getTemperature()).isEqualTo(0.5d);

		OpenAiProperties.Embedding embedding = this.openAiProperties.getEmbedding();

		assertThat(embedding).isNotNull();
		assertThat(embedding.getApiKey()).isEqualTo(this.openAiProperties.getApiKey());
		assertThat(embedding.getModel()).isEqualTo("text-embedding-ada-002");
		assertThat(embedding.getBaseUrl()).isEqualTo("https://api.openai.spring.io/embedding");
	}

	@SpringBootConfiguration
	@EnableConfigurationProperties(OpenAiProperties.class)
	static class TestConfiguration {

	}

}
