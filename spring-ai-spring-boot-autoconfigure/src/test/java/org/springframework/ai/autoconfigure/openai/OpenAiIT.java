/*
 * Copyright 2023-2023 the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.RetryAiClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.RetryEmbeddingClient;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(booleans = { false, true })
	public void embeddingClient(boolean retryEnabled) {
		contextRunner.withPropertyValues("spring.ai.openai.retryEnabled=" + retryEnabled).run(context -> {
			OpenAiProperties properties = context.getBean(OpenAiProperties.class);
			assertThat(properties.isRetryEnabled()).isEqualTo(retryEnabled);

			EmbeddingClient embeddingClient = context.getBean(EmbeddingClient.class);
			if (retryEnabled) {
				assertThat(embeddingClient).isInstanceOf(RetryEmbeddingClient.class);
			}
			else {
				assertThat(embeddingClient).isInstanceOf(OpenAiEmbeddingClient.class);
			}

			List<List<Double>> embeddings = embeddingClient.embed(List.of("Spring Framework", "Spring AI"));

			assertThat(embeddings.size()).isEqualTo(2); // batch size
			assertThat(embeddings.get(0).size()).isEqualTo(embeddingClient.dimensions()); // dimensions

			List<Double> embedding = embeddingClient.embed(new Document("test"));
			assertThat(embedding).hasSize(embeddingClient.dimensions());

			embedding = embeddingClient.embed("test");
			assertThat(embedding).hasSize(embeddingClient.dimensions());

			EmbeddingResponse response = embeddingClient.embedForResponse(List.of("test1", "test2"));

			assertThat(response).isNotNull();
			assertThat(response.data()).hasSize(2);
			assertThat(response.data().get(0).embedding()).hasSize(embeddingClient.dimensions());
			assertThat(response.data().get(1).embedding()).hasSize(embeddingClient.dimensions());
		});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(booleans = { false, true })
	public void aiClient(boolean retryEnabled) {
		contextRunner.withPropertyValues("spring.ai.openai.retryEnabled=" + retryEnabled).run(context -> {
			OpenAiProperties properties = context.getBean(OpenAiProperties.class);
			assertThat(properties.isRetryEnabled()).isEqualTo(retryEnabled);

			AiClient aiClient = context.getBean(AiClient.class);
			if (retryEnabled) {
				assertThat(aiClient).isInstanceOf(RetryAiClient.class);
			}
			else {
				assertThat(aiClient).isInstanceOf(OpenAiClient.class);
			}

			AiResponse response = aiClient.generate(new Prompt("content"));

			assertThat(response).isNotNull();
			assertThat(response.getGeneration()).isNotNull();
		});
	}

}
