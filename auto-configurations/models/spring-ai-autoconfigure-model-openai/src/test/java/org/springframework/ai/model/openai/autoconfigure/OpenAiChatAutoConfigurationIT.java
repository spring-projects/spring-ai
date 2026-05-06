/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiChatAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	void chatCall() {
		this.contextRunner
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				String response = chatModel.call("Hello");
				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void generateStreaming() {
		this.contextRunner
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
				String response = responseFlux.collectList()
					.block()
					.stream()
					.map(chatResponse -> chatResponse.getResult() != null
							? chatResponse.getResult().getOutput().getText() : "")
					.collect(Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void streamingWithTokenUsage() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.stream-usage=true")
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));

				Usage[] streamingTokenUsage = new Usage[1];
				String response = responseFlux.collectList().block().stream().map(chatResponse -> {
					streamingTokenUsage[0] = chatResponse.getMetadata().getUsage();
					return (chatResponse.getResult() != null) ? chatResponse.getResult().getOutput().getText() : "";
				}).collect(Collectors.joining());

				assertThat(streamingTokenUsage[0].getPromptTokens()).isGreaterThan(0);
				assertThat(streamingTokenUsage[0].getCompletionTokens()).isGreaterThan(0);
				assertThat(streamingTokenUsage[0].getTotalTokens()).isGreaterThan(0);

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void chatActivation() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://test.base.url")
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

		this.contextRunner
			.withPropertyValues("spring.ai.openai.api-key=API_KEY", "spring.ai.openai.base-url=http://test.base.url",
					"spring.ai.model.chat=openai")
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OpenAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
			});

	}

}
