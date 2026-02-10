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

package org.springframework.ai.model.zhipuai.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Issam El-atif
 */
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".*")
public class ZhiPuAiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(ZhiPuAiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.zhipuai.apiKey=" + System.getenv("ZHIPU_AI_API_KEY"));

	@Test
	void generate() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				ZhiPuAiChatModel chatModel = context.getBean(ZhiPuAiChatModel.class);
				ChatResponse response = chatModel.call(new Prompt("Hello", ChatOptions.builder().build()));
				assertThat(response.getResult().getOutput().getText()).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void generateStreaming() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				ZhiPuAiChatModel chatModel = context.getBean(ZhiPuAiChatModel.class);
				Flux<ChatResponse> responseFlux = chatModel
					.stream(new Prompt(new UserMessage("Hello"), ChatOptions.builder().build()));
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
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				ZhiPuAiEmbeddingModel embeddingModel = context.getBean(ZhiPuAiEmbeddingModel.class);

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
	void generateImage() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiImageAutoConfiguration.class))
			.withPropertyValues("spring.ai.zhipuai.image.options.size=1024x1024")
			.run(context -> {
				ZhiPuAiImageModel ImageModel = context.getBean(ZhiPuAiImageModel.class);
				ImageResponse imageResponse = ImageModel.call(new ImagePrompt("forest"));
				assertThat(imageResponse.getResults()).hasSize(1);
				assertThat(imageResponse.getResult().getOutput().getUrl()).isNotEmpty();
				logger.info("Generated image: " + imageResponse.getResult().getOutput().getUrl());
			});
	}

	@Test
	void generateWithCustomTimeout() {
		this.contextRunner
			.withPropertyValues("spring.ai.zhipuai.connect-timeout=1s", "spring.ai.zhipuai.read-timeout=1s")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				ZhiPuAiChatModel client = context.getBean(ZhiPuAiChatModel.class);

				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);
				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));

				String response = client.call("Hello");
				assertThat(response).isNotEmpty();
				logger.info("Response with custom timeout: " + response);
			});
	}

	@Test
	void generateStreamingWithCustomTimeout() {
		this.contextRunner
			.withPropertyValues("spring.ai.zhipuai.connect-timeout=1s", "spring.ai.zhipuai.read-timeout=1s")
			.withConfiguration(SpringAiTestAutoConfigurations.of(ZhiPuAiChatAutoConfiguration.class))
			.run(context -> {
				ZhiPuAiChatModel client = context.getBean(ZhiPuAiChatModel.class);

				// Verify that the HTTP client configuration is applied
				var connectionProperties = context.getBean(ZhiPuAiConnectionProperties.class);
				assertThat(connectionProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
				assertThat(connectionProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(1));

				Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
				String response = Objects.requireNonNull(responseFlux.collectList().block())
					.stream()
					.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
					.collect(Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response with custom timeout: " + response);
			});
	}

}
