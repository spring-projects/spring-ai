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

package org.springframework.ai.model.qianfan.autoconfigure;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.qianfan.QianFanChatModel;
import org.springframework.ai.qianfan.QianFanEmbeddingModel;
import org.springframework.ai.qianfan.QianFanImageModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "QIANFAN_SECRET_KEY", matches = ".+") })
public class QianFanAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(QianFanAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.qianfan.apiKey=" + System.getenv("QIANFAN_API_KEY"),
				"spring.ai.qianfan.secretKey=" + System.getenv("QIANFAN_SECRET_KEY"))
		.withConfiguration(
				AutoConfigurations.of(SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(QianFanChatAutoConfiguration.class)).run(context -> {
			QianFanChatModel client = context.getBean(QianFanChatModel.class);
			String response = client.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void generateStreaming() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(QianFanChatAutoConfiguration.class)).run(context -> {
			QianFanChatModel client = context.getBean(QianFanChatModel.class);
			Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
			String response = Objects.requireNonNull(responseFlux.collectList().block())
				.stream()
				.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
				.collect(Collectors.joining());
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(QianFanEmbeddingAutoConfiguration.class))
			.run(context -> {
				QianFanEmbeddingModel embeddingClient = context.getBean(QianFanEmbeddingModel.class);

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

	@Test
	void generateImage() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(QianFanImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.qianfan.image.options.size=1024x1024")
			.run(context -> {
				QianFanImageModel imageModel = context.getBean(QianFanImageModel.class);
				ImageResponse imageResponse = imageModel.call(new ImagePrompt("forest"));
				assertThat(imageResponse.getResults()).hasSize(1);
				assertThat(imageResponse.getResult().getOutput().getUrl()).isNull();
				assertThat(imageResponse.getResult().getOutput().getB64Json()).isNotEmpty();
				logger.info("Generated image: " + imageResponse.getResult().getOutput().getB64Json());
			});
	}

}
