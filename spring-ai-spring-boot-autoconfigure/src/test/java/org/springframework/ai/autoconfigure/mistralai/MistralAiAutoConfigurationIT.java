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

package org.springframework.ai.autoconfigure.mistralai;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.mistral.MistralAiChatClient;
import org.springframework.ai.mistral.MistralAiEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.1
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".*")
public class MistralAiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(MistralAiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, MistralAiAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			MistralAiChatClient client = context.getBean(MistralAiChatClient.class);
			String response = client.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void generateStreaming() {
		contextRunner.run(context -> {
			MistralAiChatClient client = context.getBean(MistralAiChatClient.class);
			Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
			String response = responseFlux.collectList().block().stream().map(chatResponse -> {
				return chatResponse.getResults().get(0).getOutput().getContent();
			}).collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		contextRunner.run(context -> {
			MistralAiEmbeddingClient embeddingClient = context.getBean(MistralAiEmbeddingClient.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(1024);
		});
	}

}
