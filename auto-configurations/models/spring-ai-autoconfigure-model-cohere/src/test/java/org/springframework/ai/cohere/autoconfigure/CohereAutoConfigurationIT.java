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

package org.springframework.ai.cohere.autoconfigure;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.ai.cohere.embedding.CohereEmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".*")
public class CohereAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(CohereAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereChatAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(CohereChatAutoConfiguration.class)).run(context -> {
			CohereChatModel chatModel = context.getBean(CohereChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class))
			.run(context -> {
				CohereEmbeddingModel embeddingModel = context.getBean(CohereEmbeddingModel.class);

				EmbeddingResponse embeddingResponse = embeddingModel
					.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
				assertThat(embeddingResponse.getResults()).hasSize(2);
				assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
				assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

				assertThat(embeddingModel.dimensions()).isEqualTo(1536);
			});
	}

	@Test
	void generateStreaming() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(CohereChatAutoConfiguration.class))
			.run(context -> {
				CohereChatModel chatModel = context.getBean(CohereChatModel.class);
				Flux<org.springframework.ai.chat.model.ChatResponse> responseFlux = chatModel
					.stream(new org.springframework.ai.chat.prompt.Prompt(
							new org.springframework.ai.chat.messages.UserMessage("Hello")));
				String response = responseFlux.collectList()
					.block()
					.stream()
					.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
					.collect(java.util.stream.Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	public void multimodalEmbedding() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(CohereMultimodalEmbeddingAutoConfiguration.class))
			.run(context -> {
				var multimodalEmbeddingProperties = context.getBean(CohereMultimodalEmbeddingProperties.class);

				assertThat(multimodalEmbeddingProperties).isNotNull();

				var multiModelEmbeddingModel = context
					.getBean(org.springframework.ai.cohere.embedding.CohereMultimodalEmbeddingModel.class);

				assertThat(multiModelEmbeddingModel).isNotNull();

				var document = new Document("Hello World");

				DocumentEmbeddingRequest embeddingRequest = new DocumentEmbeddingRequest(List.of(document),
						EmbeddingOptions.builder().build());

				EmbeddingResponse embeddingResponse = multiModelEmbeddingModel.call(embeddingRequest);
				assertThat(embeddingResponse.getResults()).hasSize(1);
				assertThat(embeddingResponse.getResults().get(0)).isNotNull();
				assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);

				assertThat(multiModelEmbeddingModel.dimensions()).isEqualTo(1536);

			});
	}

}
