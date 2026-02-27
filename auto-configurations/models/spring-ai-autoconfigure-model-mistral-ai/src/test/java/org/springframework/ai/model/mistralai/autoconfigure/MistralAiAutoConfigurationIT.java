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

package org.springframework.ai.model.mistralai.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Issam El-atif
 * @since 0.8.1
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".*")
public class MistralAiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(MistralAiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"));

	@Test
	void generate() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiChatAutoConfiguration.class))
			.run(context -> {
				MistralAiChatModel chatModel = context.getBean(MistralAiChatModel.class);
				String response = chatModel.call("Hello");
				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void generateStreaming() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiChatAutoConfiguration.class))
			.run(context -> {
				MistralAiChatModel chatModel = context.getBean(MistralAiChatModel.class);
				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
				String response = responseFlux.collectList()
					.block()
					.stream()
					.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
					.collect(Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void embedding() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				MistralAiEmbeddingModel embeddingModel = context.getBean(MistralAiEmbeddingModel.class);

				EmbeddingResponse embeddingResponse = embeddingModel
					.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
				assertThat(embeddingResponse.getResults()).hasSize(2);
				assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
				assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

				assertThat(embeddingModel.dimensions()).isEqualTo(1024);
			});
	}

	@Test
	void generateWithCustomTimeout() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(MistralAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.mistralai.connect-timeout=1ms", "spring.ai.mistralai.read-timeout=1ms")
			.run(context -> {
				MistralAiChatModel chatModel = context.getBean(MistralAiChatModel.class);

				String response = chatModel.call("Hello");
				assertThat(response).isNotNull();

				logger.info("Response with custom timeout: " + response);
			});
	}

}
